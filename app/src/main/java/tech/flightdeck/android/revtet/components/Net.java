package tech.flightdeck.android.revtet.components;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import tech.flightdeck.android.revtet.exception.InvalidCIDRException;

public final class Net {
    private Net() {
        // not instantiable
    }

    public static InetAddress[] toInetAddresses(String... addresses) {
        InetAddress[] result = new InetAddress[addresses.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = toInetAddress(addresses[i]);
        }
        return result;
    }

    public static InetAddress toInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static InetAddress toInetAddress(byte[] raw) {
        try {
            return InetAddress.getByAddress(raw);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static CIDR toCIDR(String cidr) {
        try {
            return CIDR.parse(cidr);
        } catch (InvalidCIDRException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static CIDR[] toCIDRs(String... cidrs) {
        CIDR[] result = new CIDR[cidrs.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = toCIDR(cidrs[i]);
        }
        return result;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static Inet4Address getLocalhostIPv4() {
        byte[] localhost = {127, 0, 0, 1};
        return (Inet4Address) toInetAddress(localhost);
    }
}
