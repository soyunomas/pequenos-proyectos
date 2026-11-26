package com.soyunomas.wifianalyzer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, unprivileged byte encoders/decoders for read-only local diagnostics. */
public final class FieldWire {
    private FieldWire(){}
    public static byte[] stunRequest(byte[] transaction){
        if(transaction==null||transaction.length!=12)throw new IllegalArgumentException("STUN transaction must be 12 bytes");
        byte[] out=new byte[20];out[1]=1;
        out[4]=0x21;out[5]=0x12;out[6]=(byte)0xA4;out[7]=0x42;
        System.arraycopy(transaction,0,out,8,12);
        return out;
    }
    private static int u16(byte[] b,int at){return ((b[at]&255)<<8)|(b[at+1]&255);}
    private static long u32(byte[] b,int at){return ((long)u16(b,at)<<16)|u16(b,at+2);}
    public static String stunMapped(byte[] packet,byte[] tx){
        if(tx==null||tx.length!=12||packet==null||packet.length<20||packet.length>4096||
                u16(packet,0)!=0x0101||u32(packet,4)!=0x2112A442L)
            throw new IllegalArgumentException("Respuesta STUN incorrecta");
        for(int i=0;i<12;i++)if(packet[8+i]!=tx[i])throw new IllegalArgumentException("STUN transaction incorrecta");
        int length=u16(packet,2),limit=20+length;
        if(limit>packet.length||length%4!=0)throw new IllegalArgumentException("Respuesta STUN truncada");
        for(int pos=20;pos+4<=limit;){
            int type=u16(packet,pos),size=u16(packet,pos+2),begin=pos+4;
            if(begin+size>limit)throw new IllegalArgumentException("Atributo STUN truncado");
            if((type==0x0020||type==0x0001)&&size>=8){
                int family=packet[begin+1]&255;
                int port=u16(packet,begin+2);
                if(type==0x0020)port^=0x2112;
                if(family==1&&size==8){
                    int[] bytes=new int[4];
                    for(int i=0;i<4;i++)bytes[i]=(packet[begin+4+i]&255)^(type==0x0020?(packet[4+i]&255):0);
                    return bytes[0]+"."+bytes[1]+"."+bytes[2]+"."+bytes[3]+":"+port;
                }
                if(family==2&&size==20){
                    byte[] ip=new byte[16];
                    for(int i=0;i<16;i++)
                        ip[i]=(byte)(packet[begin+4+i]^(type==0x0020?packet[4+i]:0));
                    try{return "["+java.net.InetAddress.getByAddress(ip).getHostAddress()+"]:"+port;}
                    catch(Exception ignored){}
                }
            }
            pos=begin+((size+3)/4)*4;
        }
        throw new IllegalArgumentException("Respuesta STUN sin dirección mapeada");
    }
    public static String natPmpAddress(byte[] reply){
        if(reply==null||reply.length<12||reply[0]!=0||(reply[1]&255)!=128||u16(reply,2)!=0)
            throw new IllegalArgumentException("NAT-PMP sin respuesta correcta");
        return (reply[8]&255)+"."+(reply[9]&255)+"."+(reply[10]&255)+"."+(reply[11]&255);
    }
    public static byte[] ippGetPrinterAttributes(String uri)throws IOException{
        if(uri==null||!uri.startsWith("ipp://")||uri.length()>245)
            throw new IllegalArgumentException("URI IPP incorrecta");
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        DataOutputStream d=new DataOutputStream(data);
        d.writeByte(1);d.writeByte(1);d.writeShort(0x000B);d.writeInt(1);
        d.writeByte(1);
        ippAttribute(d,0x47,"attributes-charset","utf-8");
        ippAttribute(d,0x48,"attributes-natural-language","es");
        ippAttribute(d,0x45,"printer-uri",uri);
        ippAttribute(d,0x44,"requested-attributes","printer-name");
        ippAttribute(d,0x44,"requested-attributes","printer-state");
        ippAttribute(d,0x44,"requested-attributes","printer-make-and-model");
        d.writeByte(3);
        return data.toByteArray();
    }
    private static void ippAttribute(DataOutputStream d,int tag,String key,String value)throws IOException{
        byte[] name=key.getBytes(StandardCharsets.US_ASCII);
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        d.writeByte(tag);d.writeShort(name.length);d.write(name);
        d.writeShort(bytes.length);d.write(bytes);
    }
    /** Reads only simple string/integer fields and skips all unknown IPP tags safely. */
    public static Map<String,String> ippResponse(byte[] bytes){
        if(bytes==null||bytes.length<9||bytes.length>65536)throw new IllegalArgumentException("IPP truncated");
        int pos=8;String last="";
        Map<String,String> result=new LinkedHashMap<>();
        while(pos<bytes.length){
            int tag=bytes[pos++]&255;
            if(tag==3)break;
            if(tag<=0x0F)continue;
            if(pos+2>bytes.length)throw new IllegalArgumentException("IPP name truncated");
            int nameLength=u16(bytes,pos);pos+=2;
            if(pos+nameLength+2>bytes.length)throw new IllegalArgumentException("IPP name invalid");
            String name=nameLength==0?last:new String(bytes,pos,nameLength,StandardCharsets.UTF_8);
            pos+=nameLength;
            last=name;
            int valueLength=u16(bytes,pos);pos+=2;
            if(valueLength<0||pos+valueLength>bytes.length)throw new IllegalArgumentException("IPP value invalid");
            String val;
            if(tag==0x21&&valueLength==4){
                val=Integer.toString((bytes[pos]&255)<<24|(bytes[pos+1]&255)<<16|(bytes[pos+2]&255)<<8|(bytes[pos+3]&255));
            }else if(tag>=0x40&&tag<=0x49)val=new String(bytes,pos,valueLength,StandardCharsets.UTF_8);
            else val="(tipo 0x"+Integer.toHexString(tag)+", "+valueLength+" bytes)";
            if(result.size()<40)result.put(name,val);
            pos+=valueLength;
        }
        return result;
    }
    public static String printable(byte[] b){
        return ResponseInspector.printable(b);
    }
}
