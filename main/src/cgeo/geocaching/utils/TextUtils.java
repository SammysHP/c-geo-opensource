/**
 *
 */
package cgeo.geocaching.utils;

import org.eclipse.jdt.annotation.Nullable;

import android.text.Html;
import android.text.Spanned;

import java.nio.charset.Charset;
import java.text.Collator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Misc. utils. All methods don't use Android specific stuff to use these methods in plain JUnit tests.
 */
public final class TextUtils {

    public static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
    public static final Charset CHARSET_ASCII = Charset.forName("US-ASCII");

    private static final Pattern PATTERN_REMOVE_NONPRINTABLE = Pattern.compile("\\p{Cntrl}");

    private TextUtils() {
        // utility class
    }

    /**
     * Searches for the pattern pattern in the data. If the pattern is not found defaultValue is returned
     *
     * @param data
     *            Data to search in
     * @param pattern
     *            Pattern to search for
     * @param trim
     *            Set to true if the group found should be trim'ed
     * @param group
     *            Number of the group to return if found
     * @param defaultValue
     *            Value to return if the pattern is not found
     * @param last
     *            Find the last occurring value
     * @return defaultValue or the n-th group if the pattern matches (trimmed if wanted)
     */
    @SuppressFBWarnings("DM_STRING_CTOR")
    public static String getMatch(@Nullable final String data, final Pattern pattern, final boolean trim, final int group, final String defaultValue, final boolean last) {
        if (data != null) {
            final Matcher matcher = pattern.matcher(data);
            if (matcher.find()) {
                String result = matcher.group(group);
                while (last && matcher.find()) {
                    result = matcher.group(group);
                }

                if (result != null) {
                    final Matcher remover = PATTERN_REMOVE_NONPRINTABLE.matcher(result);
                    final String untrimmed = remover.replaceAll(" ");

                    // Some versions of Java copy the whole page String, when matching with regular expressions
                    // later this would block the garbage collector, as we only need tiny parts of the page
                    // see http://developer.android.com/reference/java/lang/String.html#backing_array
                    // Thus the creation of a new String via String constructor is voluntary here!!
                    // And BTW: You cannot even see that effect in the debugger, but must use a separate memory profiler!
                    return trim ? new String(untrimmed).trim() : new String(untrimmed);
                }
            }
        }

        return defaultValue;
    }

    /**
     * Searches for the pattern pattern in the data. If the pattern is not found defaultValue is returned
     *
     * @param data
     *            Data to search in
     * @param pattern
     *            Pattern to search for
     * @param trim
     *            Set to true if the group found should be trim'ed
     * @param defaultValue
     *            Value to return if the pattern is not found
     * @return defaultValue or the first group if the pattern matches (trimmed if wanted)
     */
    public static String getMatch(final String data, final Pattern pattern, final boolean trim, final String defaultValue) {
        return getMatch(data, pattern, trim, 1, defaultValue, false);
    }

    /**
     * Searches for the pattern pattern in the data. If the pattern is not found defaultValue is returned
     *
     * @param data
     *            Data to search in
     * @param pattern
     *            Pattern to search for
     * @param defaultValue
     *            Value to return if the pattern is not found
     * @return defaultValue or the first group if the pattern matches (trimmed)
     */
    public static String getMatch(@Nullable final String data, final Pattern pattern, final String defaultValue) {
        return getMatch(data, pattern, true, 1, defaultValue, false);
    }

    /**
     * Searches for the pattern pattern in the data.
     *
     * @return true if data contains the pattern pattern
     */
    public static boolean matches(final String data, final Pattern pattern) {
        // matcher is faster than String.contains() and more flexible - it takes patterns instead of fixed texts
        return data != null && pattern.matcher(data).find();

    }

    /**
     * Replaces every \n, \r and \t with a single space. Afterwards multiple spaces
     * are merged into a single space. Finally leading spaces are deleted.
     *
     * This method must be fast, but may not lead to the shortest replacement String.
     *
     * You are only allowed to change this code if you can prove it became faster on a device.
     * see cgeo.geocaching.test.WhiteSpaceTest#replaceWhitespaceManually in the test project.
     *
     * @param data
     *            complete HTML page
     * @return the HTML page as a very long single "line"
     */
    public static String replaceWhitespace(final String data) {
        final int length = data.length();
        final char[] chars = new char[length];
        data.getChars(0, length, chars, 0);
        int resultSize = 0;
        boolean lastWasWhitespace = true;
        for (final char c : chars) {
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                if (!lastWasWhitespace) {
                    chars[resultSize++] = ' ';
                }
                lastWasWhitespace = true;
            } else {
                chars[resultSize++] = c;
                lastWasWhitespace = false;
            }
        }
        return String.valueOf(chars, 0, resultSize);
    }

    /**
     * Quick and naive check for possible rich HTML content in a string.
     *
     * @param str
     *            A string containing HTML code.
     * @return <tt>true</tt> if <tt>str</tt> contains HTML code that needs to go through a HTML renderer before
     *         being displayed, <tt>false</tt> if it can be displayed as-is without any loss
     */
    public static boolean containsHtml(final String str) {
        return str.indexOf('<') != -1 || str.indexOf('&') != -1;
    }

    /**
     * Remove all control characters (which are not valid in XML or HTML), as those should not appear in cache texts
     * anyway
     *
     */
    public static String removeControlCharacters(final String input) {
        final Matcher remover = PATTERN_REMOVE_NONPRINTABLE.matcher(input);
        return remover.replaceAll(" ").trim();
    }

    /**
     * Calculate a simple checksum for change-checking (not usable for security/cryptography!)
     *
     * @param input
     *            String to check
     * @return resulting checksum
     */
    public static long checksum(final String input) {
        final CRC32 checksum = new CRC32();
        checksum.update(input.getBytes(CHARSET_UTF8));
        return checksum.getValue();
    }

    /**
     * Build a Collator instance appropriate for comparing strings using the default locale while ignoring the casing.
     *
     * @return a collator
     */
    public static Collator getCollator() {
        final Collator collator = Collator.getInstance();
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        collator.setStrength(Collator.TERTIARY);
        return collator;
    }

    /**
     * When converting html to text using {@link Html#fromHtml(String)} then the result often contains unwanted trailing
     * linebreaks (from the conversion of paragraph tags). This method removes those.
     */
    public static CharSequence trimSpanned(final Spanned source) {
        final int length = source.length();
        int i = length;

        // loop back to the first non-whitespace character
        while (--i >= 0 && Character.isWhitespace(source.charAt(i))) {
        }

        if (i < length) {
            return source.subSequence(0, i + 1);
        }
        return source;
    }
}
