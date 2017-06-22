package codeu.chat.common;

import static org.junit.Assert.*;
import java.io.IOException;

import codeu.chat.util.Uuid;
import org.junit.Test;

/**
 * Created by dita on 5/24/17.
 */
public final class ServerInfoTest {

    @Test
    public void testEmptyArgumentConstructor() throws IOException {
        final Uuid input = Uuid.parse("1.0.0");
        final ServerInfo test = new ServerInfo();

        assertNotNull(test);
        assertEquals(test.version, input);
        assertNotEquals(test, Uuid.NULL);
    }

    @Test
    public void testUuidArgumentConstructor() throws IOException {
        final Uuid input = Uuid.parse("4.8.12");
        final ServerInfo test = new ServerInfo(input);

        assertNotNull(test);
        assertEquals(test.version, input);
        assertNotEquals(test.version, Uuid.NULL);
    }

}
