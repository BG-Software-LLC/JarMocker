package com.bgsoftware.jarmocker.logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Log {

    private static final Logger logger = Logger.getLogger("JarMocker");

    static {
        ConsoleHandler consoleHandler = new ConsoleHandler();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        consoleHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return "[" + dateFormat.format(new Date(record.getMillis())) + " " + record.getLevel() + "]: " + record.getMessage() + "\n";
            }
        });
        logger.setUseParentHandlers(false);
        logger.addHandler(consoleHandler);
    }

    private Log() {

    }

    public static void i(String tag, String message) {
        logger.info(tag + " :: " + message);
    }

    public static void w(String tag, String message) {
        logger.warning(tag + " :: " + message);
    }

    public static void w(String tag, String message, Throwable cause) {
        w(tag, message);
        cause.printStackTrace();
    }

}
