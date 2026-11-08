package com.freelabels.io;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Defensive streaming text I/O for UTF-8 and UTF-16LE/BE.
 * EOLs are recognized as CRLF, LF or CR; a CR at a chunk boundary is held
 * until the next character is available. Decoder errors are configurable.
 */
public final class TextIO {
    private TextIO() {}
    public enum Encoding { UTF8, UTF16LE, UTF16BE, AUTO }
    public enum Malformed { REPLACE, REPORT }
    public enum Eol { PRESERVE, LF, CRLF, CR }
    public static final class Options {
        public Encoding encoding = Encoding.AUTO;
        public Encoding defaultUtf16 = Encoding.UTF16LE;
        public Malformed malformed = Malformed.REPLACE;
        public Eol outputEol = Eol.PRESERVE;
        public int bufferSize = 8192;
    }
    public static final class Detected {
        public final Encoding encoding; public final int bomBytes;
        Detected(Encoding e, int b) { encoding=e; bomBytes=b; }
    }
    public static Detected detect(byte[] prefix, Encoding fallback) {
        if (prefix.length >= 3 && (prefix[0]&255)==0xEF && (prefix[1]&255)==0xBB && (prefix[2]&255)==0xBF)
            return new Detected(Encoding.UTF8,3);
        if (prefix.length >= 2 && (prefix[0]&255)==0xFF && (prefix[1]&255)==0xFE)
            return new Detected(Encoding.UTF16LE,2);
        if (prefix.length >= 2 && (prefix[0]&255)==0xFE && (prefix[1]&255)==0xFF)
            return new Detected(Encoding.UTF16BE,2);
        return new Detected(fallback==Encoding.AUTO?Encoding.UTF8:fallback,0);
    }
    private static Charset charset(Encoding e) {
        switch(e) { case UTF16LE:return StandardCharsets.UTF_16LE; case UTF16BE:return StandardCharsets.UTF_16BE;
            default:return StandardCharsets.UTF_8; }
    }
    public static BufferedReader reader(Path path, Options options) throws IOException {
        InputStream in = new BufferedInputStream(Files.newInputStream(path));
        try {
            byte[] p = new byte[3]; in.mark(3); int n=in.read(p); in.reset();
            Detected d=detect(Arrays.copyOf(p,Math.max(0,n)), Encoding.UTF8);
            Encoding selected=options.encoding==Encoding.AUTO?d.encoding:options.encoding;
            if(options.encoding!=Encoding.AUTO && d.bomBytes>0 && d.encoding!=selected)
                throw new IOException("BOM encoding ("+d.encoding+") conflicts with explicitly selected encoding ("+selected+").");
            long skip=d.bomBytes; while(skip>0){long k=in.skip(skip); if(k<=0)break; skip-=k;}
            CharsetDecoder dec=charset(selected).newDecoder();
            CodingErrorAction action=options.malformed==Malformed.REPORT?CodingErrorAction.REPORT:CodingErrorAction.REPLACE;
            dec.onMalformedInput(action).onUnmappableCharacter(action);
            return new BufferedReader(new InputStreamReader(in,dec),Math.max(128,options.bufferSize));
        } catch(Throwable t){try{in.close();}catch(IOException ignored){} throw t;}
    }
    /** Opens a streaming writer in the requested encoding, optionally emitting its BOM. */
    public static Writer writer(Path path, Encoding encoding, boolean writeBom) throws IOException {
        if (encoding == null || encoding == Encoding.AUTO)
            throw new IllegalArgumentException("Output encoding must be UTF8, UTF16LE or UTF16BE.");
        OutputStream out = new BufferedOutputStream(Files.newOutputStream(path));
        try {
            if (writeBom) {
                if (encoding == Encoding.UTF8) out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
                else if (encoding == Encoding.UTF16LE) out.write(new byte[]{(byte)0xFF,(byte)0xFE});
                else out.write(new byte[]{(byte)0xFE,(byte)0xFF});
            }
            return new BufferedWriter(new OutputStreamWriter(out, charset(encoding)));
        } catch (RuntimeException ex) { try { out.close(); } catch (IOException ignored) {} throw ex; }
    }

    /** Lines without terminators; CRLF is atomic even across read-buffer boundaries. */
    public static void forEachLine(Reader reader, int chunkSize, LineConsumer consumer) throws IOException {
        forEachLineWithEol(reader, chunkSize, (line, eol) -> consumer.accept(line));
    }
    @FunctionalInterface public interface LineConsumer { void accept(String line) throws IOException; }
    @FunctionalInterface public interface EolLineConsumer { void accept(String line, String eol) throws IOException; }
    public static void forEachLineWithEol(Reader reader, int chunkSize, EolLineConsumer consumer) throws IOException {
        char[] buf=new char[Math.max(1,chunkSize)]; StringBuilder line=new StringBuilder();
        boolean pendingCR=false; int n;
        while((n=reader.read(buf))!=-1) for(int i=0;i<n;i++){
            char c=buf[i];
            if(pendingCR){
                if(c=='\n'){consumer.accept(line.toString(),"\r\n");line.setLength(0);pendingCR=false;continue;}
                consumer.accept(line.toString(),"\r");line.setLength(0);pendingCR=false;
            }
            if(c=='\r') pendingCR=true;
            else if(c=='\n'){consumer.accept(line.toString(),"\n");line.setLength(0);}
            else line.append(c);
        }
        if(pendingCR) consumer.accept(line.toString(),"\r");
        else if(line.length()>0) consumer.accept(line.toString(),"");
    }
    /** Writes line records using preserved source terminators or an explicit target EOL. */
    public static void writeLines(Reader reader, Writer writer, int chunkSize, Eol target) throws IOException {
        final String sep=target==Eol.LF?"\n":target==Eol.CRLF?"\r\n":target==Eol.CR?"\r":null;
        forEachLineWithEol(reader,chunkSize,(line,eol)->{
            writer.write(line);
            if(!eol.isEmpty()) writer.write(sep==null?eol:sep);
        });
    }
    public static void convert(Path source, Path target, Options o) throws IOException {
        Encoding selected=o.encoding;
        if(selected==Encoding.AUTO) selected=detectFileEncoding(source,o);
        try(BufferedReader r=reader(source,o);
            Writer w=new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(target),charset(selected)),Math.max(128,o.bufferSize))){
            writeLines(r,w,o.bufferSize,o.outputEol);
        }
    }
    private static Encoding detectFileEncoding(Path p, Options o) throws IOException {
        try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[3];int n=in.read(b);
            return detect(Arrays.copyOf(b,Math.max(0,n)),Encoding.UTF8).encoding;}
    }
}
