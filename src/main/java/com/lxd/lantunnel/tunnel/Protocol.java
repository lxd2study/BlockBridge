package com.lxd.lantunnel.tunnel;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class Protocol {
    private Protocol() {
    }

    static String readLine(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value == -1) {
                return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.UTF_8);
            }
            if (value == '\n') {
                return buffer.toString(StandardCharsets.UTF_8);
            }
            if (value != '\r') {
                buffer.write(value);
            }
            if (buffer.size() > maxBytes) {
                throw new EOFException("protocol line is too long");
            }
        }
    }

    static void writeLine(OutputStream output, String line) throws IOException {
        output.write(line.getBytes(StandardCharsets.UTF_8));
        output.write('\n');
        output.flush();
    }
}
