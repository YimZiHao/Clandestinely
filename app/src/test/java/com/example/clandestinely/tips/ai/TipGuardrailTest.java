package com.example.clandestinely.tips.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TipGuardrailTest {

    private static final List<String> STORM = Collections.singletonList("thunderstorm");
    private static final List<String> HAZE = Collections.singletonList("haze_unhealthy");
    private static final List<String> RAIN_HEAT = Arrays.asList("heat", "rain");
    private static final List<String> NORMAL = Collections.singletonList("normal");

    @Test
    public void goodStormTipPasses() {
        TipGuardrail.Result r = TipGuardrail.check(
                "Lightning is likely this afternoon. Stay inside a building until the storm passes.", STORM);
        assertTrue(r.reason, r.ok);
    }

    @Test
    public void goodMalayTipPasses() {
        TipGuardrail.Result r = TipGuardrail.check(
                "Hujan lebat dijangka petang ini. Bawa payung dan berhati-hati di laluan licin.", RAIN_HEAT);
        assertTrue(r.reason, r.ok);
    }

    @Test
    public void cleansQuotesMarkdownAndSpaces() {
        TipGuardrail.Result r = TipGuardrail.check("  \"**Carry an   umbrella**, rain is likely.\"\n", RAIN_HEAT);
        assertTrue(r.reason, r.ok);
        assertEquals("Carry an umbrella, rain is likely.", r.text);
    }

    @Test
    public void rejectsEmptyShortAndLong() {
        assertFalse(TipGuardrail.check(null, NORMAL).ok);
        assertFalse(TipGuardrail.check("   ", NORMAL).ok);
        assertFalse(TipGuardrail.check("Stay dry.", NORMAL).ok);
        StringBuilder longTip = new StringBuilder();
        for (int i = 0; i < 30; i++) longTip.append("rain rain ");
        assertFalse(TipGuardrail.check(longTip.toString(), RAIN_HEAT).ok);
    }

    @Test
    public void rejectsTooManySentences() {
        assertFalse(TipGuardrail.check("Rain soon. Take an umbrella. Walk slowly. Avoid puddles.", RAIN_HEAT).ok);
    }

    @Test
    public void rejectsLinksAndPhoneNumbers() {
        assertFalse(TipGuardrail.check("Rain is coming, see www.example.com for updates.", RAIN_HEAT).ok);
        assertFalse(TipGuardrail.check("Rain is coming, call 03-7967 3200 for a lift.", RAIN_HEAT).ok);
    }

    @Test
    public void allowsEmergencyNumber() {
        assertTrue(TipGuardrail.check("Stay indoors during the storm and call 999 in an emergency.", STORM).ok);
    }

    @Test
    public void rejectsMedicineDoses() {
        TipGuardrail.Result r = TipGuardrail.check(
                "Haze is unhealthy, take 200 mg of your medicine and stay indoors.", HAZE);
        assertFalse(r.ok);
    }

    @Test
    public void rejectsUnsafeStormAdvice() {
        TipGuardrail.Result r = TipGuardrail.check(
                "Thunderstorm coming, shelter under a tree until it stops.", STORM);
        assertFalse(r.ok);
        assertTrue(r.reason.startsWith("unsafe"));
    }

    @Test
    public void negatedUnsafePhraseIsAllowed() {
        assertTrue(TipGuardrail.check(
                "Lightning risk: never wait under a tree, go inside a building instead.", STORM).ok);
        assertTrue(TipGuardrail.check(
                "The haze is unhealthy, so avoid outdoor exercise and wear a mask outside.", HAZE).ok);
        assertTrue(TipGuardrail.check(
                "Petir dijangka, jangan berteduh di bawah pokok.", STORM).ok);
    }

    @Test
    public void rejectsUnsafeHazeAdvice() {
        assertFalse(TipGuardrail.check(
                "The haze is bad today, a good time to go for a run around the lake.", HAZE).ok);
    }

    @Test
    public void rejectsOffTopicTip() {
        TipGuardrail.Result r = TipGuardrail.check("Remember to charge your phone before class today.", STORM);
        assertFalse(r.ok);
        assertTrue(r.reason.startsWith("off topic"));
    }

    @Test
    public void normalWeatherHasNoTopicCheck() {
        assertTrue(TipGuardrail.check("Nice weather today, a good time to walk between faculties.", NORMAL).ok);
    }

    @Test
    public void everyUnsafeListIsLowerCase() {
        for (String state : Arrays.asList("thunderstorm", "haze_unhealthy", "rain", "heat")) {
            for (String phrase : TipGuardrail.unsafePhrases(state)) {
                assertEquals(phrase.toLowerCase(java.util.Locale.ROOT), phrase);
            }
        }
    }
}