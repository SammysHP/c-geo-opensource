package cgeo.geocaching.filter;

import junit.framework.TestCase;

import cgeo.geocaching.models.Geocache;

import static org.assertj.core.api.Assertions.assertThat;

public class StateDisabledFilterTest extends TestCase {

    private StateFilterFactory.StateDisabledFilter disabledFilter;
    private Geocache disabledCache;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        disabledFilter = new StateFilterFactory.StateDisabledFilter();
        disabledCache = new Geocache();
        disabledCache.setDisabled(true);
    }

    public void testAccepts() {
        assertThat(disabledFilter.accepts(disabledCache)).isTrue();
        assertThat(disabledFilter.accepts(new Geocache())).isFalse();
    }

}
