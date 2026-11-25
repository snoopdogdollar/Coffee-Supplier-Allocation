package com.coffeeallocation.console;

import com.coffeeallocation.Main;
import com.coffeeallocation.model.DeliveryWeek;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryWeekConsoleTest {
    private static final DeliveryWeek ORIGINAL = new DeliveryWeek(2026, 47);

    private DeliveryWeek select(String input, ByteArrayOutputStream output) {
        try (Scanner scanner = new Scanner(input); PrintStream out = new PrintStream(output)) {
            return DeliveryWeekConsole.select(scanner, out, ORIGINAL);
        }
    }

    @Test void acceptsFutureAndHistoricalWeeks() {
        assertEquals(new DeliveryWeek(2027, 1), select("2027\n1\n", new ByteArrayOutputStream()));
        assertEquals(new DeliveryWeek(2026, 42), select("2026\n42\n", new ByteArrayOutputStream()));
    }

    @Test void invalidInputRetriesWithoutLosingTheSelection() {
        var output = new ByteArrayOutputStream();
        assertEquals(new DeliveryWeek(2026, 53),
                select("abc\n0\n10000\n2025\n53\n2026\nabc\n2026\n0\n2026\n53\n", output));
        assertTrue(output.toString().contains("1 to 52 for year 2025"));
        assertTrue(output.toString().contains("whole-number ISO week"));
    }

    @Test void blankAndEndOfInputCancelWithoutChangingTheWeek() {
        for (String input : new String[]{"", "\n", "2027\n", "2027\n\n", "2025\n53\n"}) {
            assertEquals(ORIGINAL, select(input, new ByteArrayOutputStream()));
        }
    }

    @Test void mainMenuKeepsTheSelectedWeekAndExistingExitOption() {
        var previousIn = System.in;
        var previousOut = System.out;
        var output = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream("13\n2026\n53\n13\n\n0\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output));
            Main.main(new String[0]);
        } finally {
            System.setIn(previousIn);
            System.setOut(previousOut);
        }
        assertTrue(output.toString().contains("Selected delivery period: ISO week 53/2026"));
        assertTrue(output.toString().contains("13. Change delivery ISO year/week"));
        assertTrue(output.toString().contains("Goodbye!"));
    }
}
