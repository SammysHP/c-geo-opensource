package cgeo.calendar;

import org.eclipse.jdt.annotation.NonNull;

import android.content.Intent;
import android.text.Html;

import java.util.Date;

/**
 * Add cache to calendar in Android versions 4.0 and greater using {@code Intent}. This does not require
 * calendar permissions.
 * TODO Does this work with apps other than default calendar app?
 */
class AddEntryLevel14 extends AbstractAddEntry {

    public AddEntryLevel14(@NonNull final CalendarEntry entry, @NonNull final CalendarActivity activity) {
        super(entry, activity);
    }

    @Override
    protected void addEntryToCalendarInternal() {
        final Date eventDate = entry.parseDate();
        final String description = entry.parseDescription();

        /*
         * TODO These strings are available as constants starting with API 14 and can be used when
         * targetSdkVersion changes to 14. For example CalendarContract.EXTRA_EVENT_BEGIN_TIME and
         * Events.TITLE
         */
        final Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(Compatibility.getCalendarEventsProviderURI())
                .putExtra("title", Html.fromHtml(entry.getName()).toString())
                .putExtra("description", description)
                .putExtra("hasAlarm", false)
                .putExtra("eventTimezone", "UTC");
        final long eventTime = eventDate.getTime();
        final int entryStartTimeMinutes = entry.getStartTimeMinutes();
        if (entryStartTimeMinutes >= 0) {
            intent.putExtra("beginTime", eventTime + entryStartTimeMinutes * 60000L);
        } else {
            intent.putExtra("beginTime", eventTime);
            intent.putExtra("allDay", true);
        }
        if (entry.getCoords().length() > 0) {
            intent.putExtra("eventLocation", entry.getCoords());
        }
        activity.startActivity(intent);
    }

}
