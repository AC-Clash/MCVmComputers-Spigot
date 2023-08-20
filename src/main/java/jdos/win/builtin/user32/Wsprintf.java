package jdos.win.builtin.user32;

import jdos.cpu.CPU;
import jdos.win.loader.winpe.LittleEndianFile;
import jdos.win.utils.StringUtil;

public class Wsprintf {
    // int __cdecl wsprintf(LPTSTR lpOut, LPCTSTR lpFmt, ...)
    static public int wsprintfA(int lpOut, int lpFmt) {
        String result = format(StringUtil.getString(lpFmt), false, 2);
        StringUtil.strcpy(lpOut, result);
        return result.length();
    }

    static public String format(String format, boolean wide, int argIndex) {
        int pos = format.indexOf('%');
        if (pos>=0) {
            StringBuilder buffer = new StringBuilder();
            while (pos>=0) {
                buffer.append(format, 0, pos);
                if (pos+1<format.length()) {
                    char c = format.charAt(++pos);
                    if (c == '%') {
                        buffer.append("%");
                        format = format.substring(2);
                    } else {
                        boolean leftJustify = false;
                        boolean showPlus = false;
                        boolean spaceSign = false;
                        boolean prefix = false;
                        boolean leftPadZero = false;
                        int width = 0;
                        int precision = -1;
                        boolean longValue = false;
                        boolean shortValue = false;

                        // flags
                        while (true) {
                            if (c=='-') {
                                leftJustify = true;
                            } else if (c=='+') {
                                showPlus = true;
                            } else if (c==' ') {
                                spaceSign = true;
                            } else if (c=='#') {
                                prefix = true;
                            } else if (c=='0') {
                                leftPadZero = true;
                            } else {
                                break;
                            }
                            if (pos+1<format.length()) {
                                c = format.charAt(++pos);
                            } else {
                                return buffer.toString();
                            }
                        }

                        // width
                        StringBuilder w = new StringBuilder();
                        while (c >= '0' && c <= '9') {
                            w.append(c);
                            if (pos + 1 < format.length()) {
                                c = format.charAt(++pos);
                            } else {
                                return buffer.toString();
                            }
                        }
                        if (!w.isEmpty()) {
                            width = Integer.parseInt(w.toString());
                        }

                        // precision
                        if (c=='.') {
                            if (pos+1<format.length()) {
                                c = format.charAt(++pos);
                            } else {
                                return buffer.toString();
                            }

                            StringBuilder p = new StringBuilder();
                            while (c >= '0' && c <= '9') {
                                p.append(c);
                                if (pos + 1 < format.length()) {
                                    c = format.charAt(++pos);
                                } else {
                                    return buffer.toString();
                                }
                            }
                            if (!p.isEmpty()) {
                                precision = Integer.parseInt(p.toString());
                            }
                        }

                        // length
                        if (c=='h') {
                            shortValue = true;
                            if (pos+1<format.length()) {
                                c = format.charAt(++pos);
                            } else {
                                return buffer.toString();
                            }
                        } else if (c=='l') {
                            longValue = true;
                            if (pos+1<format.length()) {
                                c = format.charAt(++pos);
                            } else {
                                return buffer.toString();
                            }
                        } else if (c=='L') {
                            longValue = true;
                            if (pos+1<format.length()) {
                                c = format.charAt(++pos);
                            } else {
                                return buffer.toString();
                            }
                        }

                        StringBuilder value = new StringBuilder();
                        StringBuilder strPrfix = new StringBuilder();
                        boolean negnumber = false;
                        if (c == 'c') {
                            if (shortValue || wide || longValue)
                                value = new StringBuilder(Character.toString((char) (CPU.CPU_Peek32(argIndex) & 0xFFFF)));
                            else
                                value = new StringBuilder(Character.toString((char) (CPU.CPU_Peek32(argIndex) & 0xFF)));
                        } else if (c == 's') {
                            if (longValue || wide)
                                value = new StringBuilder(new LittleEndianFile(CPU.CPU_Peek32(argIndex)).readCStringW());
                            else
                                value = new StringBuilder(new LittleEndianFile(CPU.CPU_Peek32(argIndex)).readCString());

                            if (precision>0 && value.length()>precision) {
                                value = new StringBuilder(value.substring(0, precision));
                            }
                        } else if (c == 'S') {
                            if (longValue || !wide)
                                value = new StringBuilder(new LittleEndianFile(CPU.CPU_Peek32(argIndex)).readCStringW());
                            else
                                value = new StringBuilder(new LittleEndianFile(CPU.CPU_Peek32(argIndex)).readCString());
                            if (precision>0 && value.length()>precision) {
                                value = new StringBuilder(value.substring(0, precision));
                            }
                        } else if (c == 'x') {
                            if (longValue) {
                                long l = (CPU.CPU_Peek32(argIndex) & 0xFFFFFFFFL) | (long) CPU.CPU_Peek32(argIndex + 1);
                                argIndex++;
                                value = new StringBuilder(Long.toString(l, 16));
                            } else {
                                value = new StringBuilder(Long.toString(CPU.CPU_Peek32(argIndex) & 0xFFFFFFFFL, 16));
                            }
                            negnumber = value.toString().startsWith("-");
                            if (negnumber)
                                value = new StringBuilder(value.substring(1));
                            if (precision==0 && value.toString().equals("0")) {
                                format = format.substring(pos);
                                continue;
                            }
                            if (prefix) {
                                strPrfix.append("0x").append(value);
                            }
                        } else if (c == 'X') {
                            if (longValue) {
                                long l = (CPU.CPU_Peek32(argIndex) & 0xFFFFFFFFL) | (long) CPU.CPU_Peek32(argIndex + 1);
                                argIndex++;
                                value = new StringBuilder(Long.toString(l, 16));
                            } else {
                                value = new StringBuilder(Long.toString(CPU.CPU_Peek32(argIndex) & 0xFFFFFFFFL, 16));
                            }
                            negnumber = value.toString().startsWith("-");
                            if (negnumber)
                                value = new StringBuilder(value.substring(1));
                            if (precision==0 && value.toString().equals("0")) {
                                format = format.substring(pos);
                                continue;
                            }
                            if (precision>0) {
                                while (value.length()<precision) {
                                    value.insert(0, "0");
                                }
                            }
                            value = new StringBuilder(value.toString().toUpperCase());
                            if (prefix) {
                                strPrfix.append("0X").append(value);
                            }
                        } else if (c == 'd') {
                            if (longValue) {
                                long l = (CPU.CPU_Peek32(argIndex) & 0xFFFFFFFFL) | (long) CPU.CPU_Peek32(argIndex + 1);
                                argIndex++;
                                value = new StringBuilder(Long.toString(l, 10));
                            } else {
                                value = new StringBuilder(Integer.toString(CPU.CPU_Peek32(argIndex), 10));
                            }
                            negnumber = value.toString().startsWith("-");
                            if (negnumber)
                                value = new StringBuilder(value.substring(1));
                            if (precision==0 && value.toString().equals("0")) {
                                format = format.substring(pos);
                                continue;
                            }
                            if (precision>0) {
                                while (value.length()<precision) {
                                    value.insert(0, "0");
                                }
                            }
                        } else if (c == 'u') {
                            if (longValue) {  // :TODO: not truly 64-bit unsigned
                                long l = CPU.CPU_Peek32(argIndex) | (long) CPU.CPU_Peek32(argIndex + 1);
                                argIndex++;
                                value = new StringBuilder(Long.toString(l, 10));
                            } else {
                                value = new StringBuilder(Long.toString(CPU.CPU_Peek32(argIndex) & 0xFFFFFFFFL, 10));
                            }
                            negnumber = value.toString().startsWith("-");
                            if (negnumber)
                                value = new StringBuilder(value.substring(1));
                            if (precision==0 && value.toString().equals("0")) {
                                format = format.substring(pos);
                                continue;
                            }
                            if (precision>0) {
                                while (value.length()<precision) {
                                    value.insert(0, "0");
                                }
                            }
                        }else if (c == 'f') {
                            value = new StringBuilder(Float.toString(Float.intBitsToFloat(CPU.CPU_Peek32(argIndex))));
                            negnumber = value.toString().startsWith("-");
                            if (negnumber)
                                value = new StringBuilder(value.substring(1));
                            int dec = value.toString().indexOf('.');
                            if (dec>=0) {
                                if (precision==0) {
                                    value = new StringBuilder(value.substring(0, dec));
                                } else if (value.length()>dec+1+precision) {
                                    value = new StringBuilder(value.substring(0, dec + 1 + precision));
                                }
                            }
                        }

                        if (negnumber) {
                            strPrfix = new StringBuilder("-");
                        } else {
                            if (showPlus) {
                                strPrfix.insert(0, "+");
                            } else if (spaceSign) {
                                strPrfix.insert(0, " ");
                            }
                        }
                        while (width>strPrfix.length()+value.length()) {
                            if (leftPadZero) {
                                strPrfix.append("0");
                            } else if (leftJustify) {
                                value.append(" ");
                            } else {
                                strPrfix.insert(0, " ");
                            }
                        }
                        buffer.append(strPrfix);
                        buffer.append(value);
                        format = format.substring(++pos);
                    }
                }
                argIndex++;
                pos = format.indexOf('%');
            }
            buffer.append(format);
            return buffer.toString();
        } else {
            return format;
        }
    }
}
