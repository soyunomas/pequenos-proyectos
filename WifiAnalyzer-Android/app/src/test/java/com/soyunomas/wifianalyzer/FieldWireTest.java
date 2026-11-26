package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.Arrays;
import java.util.Map;
import static org.junit.Assert.*;

public class FieldWireTest {
    @Test public void stunRequestCarriesMagicCookieAndTransaction(){
        byte[] tx={0,1,2,3,4,5,6,7,8,9,10,11};
        byte[] request=FieldWire.stunRequest(tx);
        assertEquals(20,request.length);
        assertEquals((byte)0x00,request[0]);
        assertEquals((byte)0x01,request[1]);
        assertEquals((byte)0x21,request[4]);
        assertEquals((byte)0x12,request[5]);
        assertEquals((byte)0xA4,request[6]);
        assertEquals((byte)0x42,request[7]);
        assertEquals(11,request[19]);
    }
    @Test public void decodesXorMappedAddressFromValidStunResponse(){
        byte[] tx={0,1,2,3,4,5,6,7,8,9,10,11};
        byte[] reply=new byte[32];
        reply[0]=1;reply[1]=1;reply[3]=12;
        reply[4]=0x21;reply[5]=0x12;reply[6]=(byte)0xA4;reply[7]=0x42;
        System.arraycopy(tx,0,reply,8,12);
        reply[20]=0;reply[21]=0x20;reply[23]=8;
        reply[24]=0;reply[25]=1;
        int port=54321^0x2112;
        reply[26]=(byte)(port>>8);reply[27]=(byte)port;
        int[] cookie={0x21,0x12,0xA4,0x42};
        int[] ip={203,0,113,8};
        for(int i=0;i<4;i++)reply[28+i]=(byte)(cookie[i]^ip[i]);
        assertEquals("203.0.113.8:54321",FieldWire.stunMapped(reply,tx));
    }
    @Test(expected=IllegalArgumentException.class)
    public void stunRejectsMismatchedRequestId(){
        byte[] bytes=new byte[32];bytes[0]=1;bytes[1]=1;
        bytes[4]=0x21;bytes[5]=0x12;bytes[6]=(byte)0xA4;bytes[7]=0x42;
        bytes[8]=42;
        FieldWire.stunMapped(bytes,new byte[12]);
    }
    @Test public void natPmpExternalAddress(){
        byte[] response={0,(byte)0x80,0,0,0,0,0,4,(byte)198,51,100,16};
        assertEquals("198.51.100.16",FieldWire.natPmpAddress(response));
    }
    @Test public void ippRequestAndParsedResponse()throws Exception{
        byte[] query=FieldWire.ippGetPrinterAttributes("ipp://192.168.1.4:631/ipp/print");
        assertEquals(1,query[0]);assertEquals(1,query[1]);
        assertEquals(0x0B,query[3]&255);
        assertEquals(3,query[query.length-1]);
        // Test a valid short response composed from the same IPP tags:
        byte[] actual=new byte[]{1,1,0,0,0,0,0,1,4,0x42,0,4,110,97,109,101,0,4,116,101,115,116,3};
        Map<String,String> attrs=FieldWire.ippResponse(actual);
        assertEquals("test",attrs.get("name"));
    }
}
