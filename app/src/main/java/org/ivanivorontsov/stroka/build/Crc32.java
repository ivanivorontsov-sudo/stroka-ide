package org.ivanivorontsov.stroka.build;

final class Crc32 {
    private static final int[] T = new int[256];
    static {
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
            T[n] = c;
        }
    }
    static int of(byte[] b) {
        int crc = 0xFFFFFFFF;
        for (byte v : b) crc = T[(crc ^ (v & 0xFF)) & 0xFF] ^ (crc >>> 8);
        return crc ^ 0xFFFFFFFF;
    }
    private Crc32() {}
}
