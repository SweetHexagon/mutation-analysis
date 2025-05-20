package com.example;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class ErrorFilterPrintStream extends PrintStream {
    /**
     * All stderr lines matching *any* of these patterns will be dropped.
     * You can reassign this in your main() if you need different filters.
     */
    public static List<Pattern> FILTER_PATTERNS = List.of(
            Pattern.compile("wrongly tagged as containing missing types")
    );

    private final PrintStream delegate;

    public ErrorFilterPrintStream(PrintStream delegate) {
        // we never actually write to this internal stream:
        super(new OutputStream() {
            @Override
            public void write(int b) { /* no-op */ }
        });
        this.delegate = delegate;
    }

    @Override
    public void println(String x) {
        if (!shouldFilter(x)) {
            delegate.println(x);
        }
    }

    @Override
    public void print(String s) {
        if (!shouldFilter(s)) {
            delegate.print(s);
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        String chunk = new String(buf, off, len);
        if (!shouldFilter(chunk)) {
            delegate.write(buf, off, len);
        }
    }

    private boolean shouldFilter(String msg) {
        if (msg == null) return false;
        for (Pattern p : FILTER_PATTERNS) {
            if (p.matcher(msg).find()) {
                return true;
            }
        }
        return false;
    }
}
