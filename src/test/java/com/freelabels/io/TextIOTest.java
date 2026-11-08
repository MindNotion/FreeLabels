package com.freelabels.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Dependency-free regression tests; run with assertions enabled or execute main(). */
public final class TextIOTest {
    public static void main(String[] args) throws Exception {
        assertLines("", new String[]{});
        assertLines("a\nb\r\nc\rd", new String[]{"a","b","c","d"});
        assertLines("😀 café 漢字", new String[]{"😀 café 漢字"});
        assertLines("a\r\nb", new String[]{"a","b"},1); // CRLF split at one-char chunks
        Path dir=Files.createTempDirectory("textio-test");
        try {
            byte[] utf8={(byte)0xEF,(byte)0xBB,(byte)0xBF,'A',(byte)0xF0,(byte)0x9F,(byte)0x98,(byte)0x80};
            Path f=dir.resolve("utf8.txt");Files.write(f,utf8);
            TextIO.Options o=new TextIO.Options();o.encoding=TextIO.Encoding.AUTO;
            try(Reader r=TextIO.reader(f,o)){StringBuilder b=new StringBuilder();int c;while((c=r.read())!=-1)b.append((char)c);
                check("A😀".equals(b.toString()),"UTF-8 BOM and supplementary code point");}
            Path le=dir.resolve("le.txt");Files.write(le,new byte[]{(byte)0xFF,(byte)0xFE,0x3D,(byte)0xD8,0x00,(byte)0xDE});
            try(Reader r=TextIO.reader(le,o)){StringBuilder b=new StringBuilder();int c;while((c=r.read())!=-1)b.append((char)c);
                check("😀".equals(b.toString()),"UTF-16LE BOM/surrogate pair");}
            Path be=dir.resolve("be.txt");Files.write(be,new byte[]{(byte)0xFE,(byte)0xFF,(byte)0xD8,0x3D,(byte)0xDE,0x00});
            try(Reader r=TextIO.reader(be,o)){StringBuilder b=new StringBuilder();int c;while((c=r.read())!=-1)b.append((char)c);
                check("😀".equals(b.toString()),"UTF-16BE BOM/surrogate pair");}
            Path write16=dir.resolve("write16.txt");
            try(Writer w=TextIO.writer(write16,TextIO.Encoding.UTF16BE,true)){w.write("BOM 😀");}
            try(Reader r=TextIO.reader(write16,o)){StringBuilder b=new StringBuilder();int c;while((c=r.read())!=-1)b.append((char)c);
                check("BOM 😀".equals(b.toString()),"UTF-16BE writer/BOM");}
            Path malformed=dir.resolve("malformed.txt");Files.write(malformed,new byte[]{(byte)0xC3,0x28});
            o.malformed=TextIO.Malformed.REPLACE;
            try(Reader r=TextIO.reader(malformed,o)){StringBuilder b=new StringBuilder();int c;while((c=r.read())!=-1)b.append((char)c);
                check(b.indexOf("\uFFFD")>=0,"Malformed UTF-8 replacement");}
            o.malformed=TextIO.Malformed.REPORT;
            boolean strictFailed=false;
            try(Reader r=TextIO.reader(malformed,o)){while(r.read()!=-1){}}
            catch(java.nio.charset.MalformedInputException expected){strictFailed=true;}
            check(strictFailed,"Strict malformed UTF-8 rejection");
            o.malformed=TextIO.Malformed.REPLACE;
            Path mixed=dir.resolve("mixed.txt"), copy=dir.resolve("copy.txt");
            Files.write(mixed,"x\r\ny\nz\r".getBytes(StandardCharsets.UTF_8));
            TextIO.convert(mixed,copy,o);
            check(java.util.Arrays.equals(Files.readAllBytes(mixed),Files.readAllBytes(copy)),"Preserve mixed EOLs");
            o.outputEol=TextIO.Eol.LF;Path lf=dir.resolve("lf.txt");TextIO.convert(mixed,lf,o);
            check("x\ny\nz\n".equals(new String(Files.readAllBytes(lf),StandardCharsets.UTF_8)),"EOL conversion");
        } finally { try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(IOException ignored){}});} }
        System.out.println("TextIO tests passed");
    }
    private static void assertLines(String text,String[] expected)throws Exception{assertLines(text,expected,2);}
    private static void assertLines(String text,String[] expected,int chunk)throws Exception{
        java.util.List<String> got=new java.util.ArrayList<>();
        TextIO.forEachLine(new StringReader(text),chunk,got::add);
        check(got.equals(java.util.Arrays.asList(expected)),"Line parsing: "+got);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
