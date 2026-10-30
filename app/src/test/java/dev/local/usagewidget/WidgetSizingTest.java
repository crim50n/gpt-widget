package dev.local.usagewidget;

import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetSizingTest {
    @Test public void proportionsSelectDistinctCompositions() {
        assertEquals(WidgetSizing.Kind.MINI, WidgetSizing.kind(76, 76));
        assertEquals(WidgetSizing.Kind.RAIL, WidgetSizing.kind(76, 170));
        assertEquals(WidgetSizing.Kind.RAIL, WidgetSizing.kind(76, 350));
        assertEquals(WidgetSizing.Kind.STRIP, WidgetSizing.kind(170, 76));
        assertEquals(WidgetSizing.Kind.PANEL, WidgetSizing.kind(170, 260));
    }
    @Test public void tallNarrowWidgetShowsUsefulInformation() {
        WidgetSizing.Layout l = WidgetSizing.choose(170, 380, 1);
        assertTrue(l.stats()); assertTrue(l.expiry()); assertTrue(l.status());
    }
    @Test public void smallWidgetPrioritizesDateOverCards() {
        WidgetSizing.Layout l = WidgetSizing.choose(150, 170, 1);
        assertFalse(l.stats()); assertFalse(l.expiry()); assertFalse(l.status());
    }
    @Test public void largerFontsReduceOptionalContent() {
        WidgetSizing.Layout normal = WidgetSizing.choose(300, 350, 1);
        WidgetSizing.Layout enlarged = WidgetSizing.choose(300, 350, 1.6f);
        assertTrue(normal.expiry()); assertFalse(enlarged.expiry());
    }
    @Test public void oversizedWidgetCanShowTicketList() {
        assertTrue(WidgetSizing.choose(350, 650, 1).tickets());
    }
}
