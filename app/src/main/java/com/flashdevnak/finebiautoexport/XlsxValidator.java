package com.flashdevnak.finebiautoexport;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class XlsxValidator {
    public static final class Result {
        public final boolean valid;
        public final String message;

        Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
    }

    private static final Set<String> REQUIRED_SHEETS = new HashSet<>(
            Arrays.asList("th_update_time", "汇总数据", "明细数据", "仪表板")
    );

    private XlsxValidator() {}

    public static Result validate(File file, String expectedUpdateTime) {
        try {
            byte[] first = new byte[2];
            try (InputStream in = new java.io.FileInputStream(file)) {
                if (in.read(first) != 2 || first[0] != 0x50 || first[1] != 0x4B) {
                    return new Result(false, "not XLSX/ZIP");
                }
            }

            boolean timestampFound = false;
            boolean workbookFound = false;
            Set<String> sheets = new HashSet<>();

            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry workbook = zip.getEntry("xl/workbook.xml");
                if (workbook != null) {
                    workbookFound = true;
                    String xml = readLimited(zip.getInputStream(workbook), 2_000_000);
                    for (String required : REQUIRED_SHEETS) {
                        if (xml.contains(required)) sheets.add(required);
                    }
                }

                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements() && !timestampFound) {
                    ZipEntry e = entries.nextElement();
                    if (!e.isDirectory() && e.getName().endsWith(".xml")) {
                        String xml = readLimited(zip.getInputStream(e), 8_000_000);
                        if (xml.contains(expectedUpdateTime)) {
                            timestampFound = true;
                        }
                    }
                }
            }

            boolean ok = workbookFound
                    && sheets.containsAll(REQUIRED_SHEETS)
                    && timestampFound;

            return new Result(
                    ok,
                    "zip=true sheets=" + sheets.size() + "/4 timestamp=" + timestampFound
            );
        } catch (Exception e) {
            return new Result(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String readLimited(InputStream in, int limit) throws Exception {
        try (InputStream input = in;
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int total = 0;
            int n;
            while ((n = input.read(buf)) >= 0) {
                if (total + n > limit) {
                    n = limit - total;
                }
                if (n > 0) {
                    bos.write(buf, 0, n);
                    total += n;
                }
                if (total >= limit) break;
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
