package com.thehieu.standby;

/** Đổi dương lịch → âm lịch Việt Nam (thuật toán Hồ Ngọc Đức, múi giờ +7). */
final class Lunar {
    static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};
    static final double TZ = 7.0;

    static final String[] MONTH_NAMES = {"", "Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Một", "Chạp"};
    static final String[] GIO_HD = {"110100101100", "001101001011", "110011010010", "101100110100", "001011001101", "010010110011"};

    private Lunar() {}

    static String dayCanChi(int jd) { return CAN[(jd + 9) % 10] + " " + CHI[(jd + 1) % 12]; }

    static String monthCanChi(int lunarMonth, int lunarYear) {
        return CAN[(lunarYear * 12 + lunarMonth + 3) % 10] + " " + CHI[(lunarMonth + 1) % 12];
    }

    static String monthName(int m) { return m == 11 ? "Mười Một" : MONTH_NAMES[m]; }

    /** Các giờ hoàng đạo trong ngày (theo chi của ngày). */
    static String gioHoangDao(int jd) {
        String g = GIO_HD[((jd + 1) % 12) % 6];
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (g.charAt(i) == '1') {
                if (b.length() > 0) b.append(", ");
                b.append(CHI[i]);
            }
        }
        return b.toString();
    }

    static String yearName(int lunarYear) {
        return CAN[(lunarYear + 6) % 10] + " " + CHI[(lunarYear + 8) % 12];
    }

    static int jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        int jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        return jd;
    }

    static int newMoonDay(int k) {
        double T = k / 1236.85, T2 = T * T, T3 = T2 * T, dr = Math.PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        double M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        double Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        double F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        double C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
        C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
        C1 = C1 - 0.0004 * Math.sin(dr * 3 * Mpr);
        C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
        C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
        C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
        C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
        double deltat = T < -11
                ? 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
                : -0.000278 + 0.000265 * T + 0.000262 * T2;
        double jdNew = jd1 + C1 - deltat;
        return (int) Math.floor(jdNew + 0.5 + TZ / 24);
    }

    static int sunLongitude(int jdn) {
        double T = (jdn - 2451545.5 - TZ / 24) / 36525, T2 = T * T, dr = Math.PI / 180;
        double M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        double L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        double DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
        DL += (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
        double L = (L0 + DL) * dr;
        L = L - Math.PI * 2 * Math.floor(L / (Math.PI * 2));
        return (int) Math.floor(L / Math.PI * 6);
    }

    static int lunarMonth11(int yy) {
        double off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        int nm = newMoonDay(k);
        if (sunLongitude(nm) >= 9) nm = newMoonDay(k - 1);
        return nm;
    }

    static int leapMonthOffset(int a11) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int last, i = 1;
        int arc = sunLongitude(newMoonDay(k + i));
        do {
            last = arc;
            i++;
            arc = sunLongitude(newMoonDay(k + i));
        } while (arc != last && i < 14);
        return i - 1;
    }

    /** @return {ngày, tháng, năm, nhuận(0/1)} */
    static int[] fromSolar(int dd, int mm, int yy) {
        int dayNumber = jdFromDate(dd, mm, yy);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        int monthStart = newMoonDay(k + 1);
        if (monthStart > dayNumber) monthStart = newMoonDay(k);
        int a11 = lunarMonth11(yy), b11, lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            b11 = a11;
            a11 = lunarMonth11(yy - 1);
        } else {
            lunarYear = yy + 1;
            b11 = lunarMonth11(yy + 1);
        }
        int lunarDay = dayNumber - monthStart + 1;
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        int leap = 0;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapDiff = leapMonthOffset(a11);
            if (diff >= leapDiff) {
                lunarMonth = diff + 10;
                if (diff == leapDiff) leap = 1;
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        return new int[]{lunarDay, lunarMonth, lunarYear, leap};
    }
}
