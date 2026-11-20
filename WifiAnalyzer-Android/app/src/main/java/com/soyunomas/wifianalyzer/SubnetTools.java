package com.soyunomas.wifianalyzer;

/** RFC-compatible IPv4 CIDR calculator, with explicit handling of /31 and /32. */
public final class SubnetTools {
    private SubnetTools() {}

    public static final class Subnet {
        public final String ip, mask, network, broadcast, first, last;
        public final int prefix;
        public final long totalAddresses, usableHosts;
        private Subnet(String ip, int prefix, long mask, long network, long broadcast) {
            this.ip=ip;this.prefix=prefix;this.mask=fmt(mask);
            this.network=fmt(network);this.broadcast=fmt(broadcast);
            this.totalAddresses=1L << (32-prefix);
            this.usableHosts=prefix>=31?totalAddresses:totalAddresses-2;
            this.first=fmt(prefix>=31?network:network+1);
            this.last=fmt(prefix>=31?broadcast:broadcast-1);
        }
    }

    public static Subnet calculate(String cidr) {
        if(cidr==null) throw new IllegalArgumentException("Introduce una IPv4/CIDR.");
        String[] parts=cidr.trim().split("/",-1);
        if(parts.length!=2)throw new IllegalArgumentException("Formato esperado: 192.168.1.10/24");
        int prefix;
        try{prefix=Integer.parseInt(parts[1]);}catch(NumberFormatException e){
            throw new IllegalArgumentException("Prefijo CIDR no válido.");
        }
        if(prefix<0||prefix>32)throw new IllegalArgumentException("El prefijo debe estar entre 0 y 32.");
        String[] octets=parts[0].split("\\.",-1);
        if(octets.length!=4)throw new IllegalArgumentException("La IPv4 debe tener cuatro octetos.");
        long ip=0;
        for(String octet:octets){
            if(octet.isEmpty()||octet.length()>3)throw new IllegalArgumentException("Octeto IPv4 incorrecto.");
            for(int i=0;i<octet.length();i++)
                if(!Character.isDigit(octet.charAt(i)))throw new IllegalArgumentException("La IPv4 solo acepta números.");
            int x=Integer.parseInt(octet);
            if(x<0||x>255)throw new IllegalArgumentException("Un octeto supera 255.");
            ip=(ip<<8)|x;
        }
        long mask=prefix==0?0:(0xFFFFFFFFL<<(32-prefix))&0xFFFFFFFFL;
        long net=ip&mask;
        long broad=net|(~mask&0xFFFFFFFFL);
        return new Subnet(fmt(ip),prefix,mask,net,broad);
    }

    static String fmt(long val){
        return ((val>>>24)&255)+"."+((val>>>16)&255)+"."+((val>>>8)&255)+"."+(val&255);
    }
}
