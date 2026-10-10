package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.nav.LatLon
import com.aiconsole.kittlauncher2.nav.Place
import com.aiconsole.kittlauncher2.nav.Route
import com.aiconsole.kittlauncher2.nav.RouteRules
import com.aiconsole.kittlauncher2.nav.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRulesTest {

    private fun step(type: String, modifier: String? = null, name: String = "", exit: Int? = null, at: LatLon = LatLon(0.0, 0.0)) =
        RouteStep(type, modifier, name, null, exit, at, 0.0, 0.0)

    @Test
    fun decodesGooglePolylineExample() {
        // The worked example from Google's encoded polyline documentation (precision 5).
        val points = RouteRules.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-6)
        assertEquals(-120.2, points[0].lon, 1e-6)
        assertEquals(40.7, points[1].lat, 1e-6)
        assertEquals(-120.95, points[1].lon, 1e-6)
        assertEquals(43.252, points[2].lat, 1e-6)
        assertEquals(-126.453, points[2].lon, 1e-6)
    }

    @Test
    fun distanceIsHaversine() {
        // One degree of latitude is about 111.2 km.
        assertEquals(111_195.0, RouteRules.distanceM(LatLon(0.0, 0.0), LatLon(1.0, 0.0)), 50.0)
    }

    @Test
    fun instructionsReadNaturally() {
        assertEquals("Turn left onto Main Street", RouteRules.instruction(step("turn", "left", "Main Street")))
        assertEquals("Turn slight right", RouteRules.instruction(step("turn", "slight right")))
        assertEquals("Make a U-turn", RouteRules.instruction(step("turn", "uturn", "Elm")))
        assertEquals("Take the exit on the right onto I-25", RouteRules.instruction(step("off ramp", "slight right", "I-25")))
        assertEquals("At the roundabout, take the second exit onto Oak Road", RouteRules.instruction(step("roundabout", "right", "Oak Road", exit = 2)))
        assertEquals("Arrive at your destination, on the left", RouteRules.instruction(step("arrive", "left")))
        assertEquals("Keep left at the fork", RouteRules.instruction(step("fork", "slight left")))
    }

    @Test
    fun distancesInBothUnits() {
        assertEquals("500 ft", RouteRules.shortDistance(152.4, metric = false))
        assertEquals("0.5 mi", RouteRules.shortDistance(804.672, metric = false))
        assertEquals("12 mi", RouteRules.shortDistance(12 * 1609.344, metric = false))
        assertEquals("300 m", RouteRules.shortDistance(300.0, metric = true))
        assertEquals("1.4 km", RouteRules.shortDistance(1_400.0, metric = true))
        assertEquals("2 km", RouteRules.shortDistance(2_000.0, metric = true))
        assertEquals("half a mile", RouteRules.spokenDistance(800.0, metric = false))
        assertEquals("a quarter mile", RouteRules.spokenDistance(400.0, metric = false))
        assertEquals("500 feet", RouteRules.spokenDistance(152.4, metric = false))
        assertEquals("2.5 miles", RouteRules.spokenDistance(2.5 * 1609.344, metric = false))
        assertEquals("3 kilometres", RouteRules.spokenDistance(3_000.0, metric = true))
        assertEquals("8 min", RouteRules.duration(480.0))
        assertEquals("1 h 5 min", RouteRules.duration(3_900.0))
    }

    @Test
    fun promptsComeEarlierOnFasterRoads() {
        assertNull(RouteRules.promptLevel(1_000.0, 30.0))
        assertEquals(1, RouteRules.promptLevel(350.0, 30.0))
        assertEquals(2, RouteRules.promptLevel(50.0, 30.0))
        assertEquals(1, RouteRules.promptLevel(1_500.0, 100.0))
        // At 100 km/h "now" comes about 7 seconds (~194 m) out.
        assertEquals(2, RouteRules.promptLevel(180.0, 100.0))
        assertTrue(RouteRules.isOffRoute(80.0))
        assertFalse(RouteRules.isOffRoute(30.0))
        assertFalse(RouteRules.isOffRoute(80.0, accuracyM = 50.0))
    }

    @Test
    fun progressFollowsTheRoute() {
        // Straight north along a meridian, ~1.1 km per 0.01°, turning at the middle point.
        val points = listOf(LatLon(0.0, 0.0), LatLon(0.01, 0.0), LatLon(0.02, 0.0))
        val steps = listOf(
            step("depart", at = points[0]),
            step("turn", "left", "Side St", at = points[1]),
            step("arrive", at = points[2])
        )
        val route = Route(points, steps, 2_224.0, 200.0)
        val cumulative = RouteRules.cumulative(points)
        val stepPoints = RouteRules.stepPoints(route)
        assertEquals(listOf(0, 1, 2), stepPoints.toList())

        val p = RouteRules.progress(route, cumulative, stepPoints, LatLon(0.005, 0.0001), 0)
        assertEquals(1, p.nextStep)
        assertEquals(556.0, p.toNextM, 5.0)
        assertEquals(1_668.0, p.remainingM, 5.0)
        assertEquals(11.0, p.offRouteM, 1.0)

        val later = RouteRules.progress(route, cumulative, stepPoints, LatLon(0.015, 0.0), p.pointIndex)
        assertEquals(2, later.nextStep)
        assertEquals(556.0, later.remainingM, 5.0)
    }

    @Test
    fun recentsRoundTripAndDeduplicate() {
        val a = Place("Home Depot", "Main St, Denver", LatLon(39.7, -104.9))
        val b = Place("Gas | Station", "Line\nbreak", LatLon(39.8, -105.0))
        val decoded = RouteRules.decodeRecents(RouteRules.encodeRecents(listOf(a, b)))
        assertEquals(2, decoded.size)
        assertEquals(a, decoded[0])
        assertEquals("Gas   Station", decoded[1].name)
        val again = RouteRules.remember(decoded, a.copy(location = LatLon(39.7001, -104.9)))
        assertEquals(2, again.size)
        assertEquals(39.7001, again[0].location.lat, 1e-9)
        assertEquals(3, RouteRules.remember(listOf(a, b, a.copy(name = "x")), b, max = 3).size)
    }

    @Test
    fun unfilteredDirectionsKeepTheInstructionFirst() {
        val plain = "In half a mile, turn left onto Main Street."
        assertEquals(plain, RouteRules.spoken(RouteRules.Speech.Early, plain, unfiltered = false, roll = 3))
        for (roll in -5..10) {
            for (kind in listOf(RouteRules.Speech.Start, RouteRules.Speech.Early, RouteRules.Speech.Now)) {
                val line = RouteRules.spoken(kind, plain, unfiltered = true, roll = roll)
                assertTrue(line, line.startsWith(plain) && line.length > plain.length)
            }
            val arrive = RouteRules.spoken(RouteRules.Speech.Arrive, "You have arrived at Walmart.", unfiltered = true, roll = roll)
            assertTrue(arrive, arrive.startsWith("You have arrived at Walmart"))
            assertTrue(RouteRules.spoken(RouteRules.Speech.Reroute, "Recalculating.", unfiltered = true, roll = roll).startsWith("Recalculating"))
        }
    }

    @Test
    fun spokenDestinationDropsFiller() {
        assertEquals("walmart", RouteRules.destinationQuery("the nearest walmart"))
        assertEquals("gas station", RouteRules.destinationQuery("a gas station"))
        assertEquals("1600 pennsylvania avenue", RouteRules.destinationQuery("1600 pennsylvania avenue"))
    }
}
