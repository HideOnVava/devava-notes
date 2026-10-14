package com.devavaxp.notes;

import java.time.LocalDate;

/** A school period of the Tec: Ene–Jun, Verano or Ago–Dic of a year, written "Ago–Dic 2026". */
record Period(int year, int part) implements Comparable<Period> {

    private static final String[] NAMES = {"Ene–Jun", "Verano", "Ago–Dic"};

    /** The period a day falls in: January to June, July (summer courses), or August to December. */
    static Period of(LocalDate day) {
        int month = day.getMonthValue();
        return new Period(day.getYear(), month <= 6 ? 0 : month == 7 ? 1 : 2);
    }

    /** "Ago–Dic 2026" back into its period; null for anything else. */
    static Period parse(String text) {
        for (int part = 0; part < NAMES.length; part++) {
            if (!text.startsWith(NAMES[part] + " ")) continue;
            try {
                return new Period(Integer.parseInt(text.substring(NAMES[part].length() + 1).strip()), part);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    Period next() {
        return part == 2 ? new Period(year + 1, 0) : new Period(year, part + 1);
    }

    Period previous() {
        return part == 0 ? new Period(year - 1, 2) : new Period(year, part - 1);
    }

    @Override
    public int compareTo(Period other) {
        return year != other.year ? Integer.compare(year, other.year) : Integer.compare(part, other.part);
    }

    @Override
    public String toString() {
        return NAMES[part] + " " + year;
    }
}
