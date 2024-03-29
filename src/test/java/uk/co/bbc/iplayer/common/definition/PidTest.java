package uk.co.bbc.iplayer.common.definition;

import com.google.common.collect.Lists;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.Serializable;
import java.util.List;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class PidTest {

    // 21 char string
    public static final String PID_EXCEEDING_MAX_LENGTH = "123456789bcdfghjklmnp";
    public static final String PID_AT_MAX_LENGTH = "123456789bcdfghjklmn";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static void valid(String pid) {
        assertThat(pid, is(new Pid(pid).getId()));
    }

    @Test
    public void pidIsAlphanumber() throws Exception {
        valid("b0144p0z");
    }

    @Test
    public void pidCanBe8CharsOrMore() {
        valid("b1234567");
    }

    @Test
    public void pidMustNotExceedMaxLengthOf20Chars() {
        expectedException.expect(IllegalArgumentException.class);
        valid(PID_EXCEEDING_MAX_LENGTH);
    }

    @Test
    public void pidMatchesOnMaxBoundary() {
        valid(PID_AT_MAX_LENGTH);
    }

    @Test
    public void rejectIllegalChars() {
        expectedException.expect(IllegalArgumentException.class);
        valid("&#x9090-11_+-*dd(n)d");
    }

    @Test
    public void pidMatchOnDashAndUnderscores() {
        valid("b0144p0z-asd4f_AaZz_");
    }
    @Test
    public void pidCanBeJustLetters() {
        valid("bnmcbnmc");
    }
    @Test
    public void pidCanBeJustNumbers() {
        valid("12345678");
    }

    @Test
    public void pidCantHaveVowels() {
        new Pid("1234567a");
    }

    @Test
    public void testFromPids() throws Exception {
        List<String> strings = Lists.newArrayList("b0000001", "d0000001");
        List<Pid> pids = Lists.newArrayList();
        for (String string : strings) {
            pids.add(new Pid(string));
        }

        assertThat(strings, is(Pid.fromPids(pids)));
    }

    @Test
    public void implementsSerializableForCaching() {
        MatcherAssert.assertThat(DataId.create("id"), instanceOf(Serializable.class));
    }
}
