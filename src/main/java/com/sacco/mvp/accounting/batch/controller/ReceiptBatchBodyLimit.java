package com.sacco.mvp.accounting.batch.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;

/** A single edited receipt is bounded before JSON binding, including chunked requests. */
@Component
public class ReceiptBatchBodyLimit extends OncePerRequestFilter {
    private static final int LIMIT=128*1024;
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith(request.getContextPath()+"/finance/batches/receipts/") || !"application/json".equalsIgnoreCase(request.getContentType()==null?"":request.getContentType().split(";")[0].trim()) || !(request.getMethod().equals("PUT") || request.getMethod().equals("POST"));}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if(request.getContentLengthLong()>LIMIT){response.sendError(413);return;}byte[] body=request.getInputStream().readNBytes(LIMIT+1);if(body.length>LIMIT){response.sendError(413);return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){var bytes=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return bytes.read();}public int read(byte[] b,int off,int len){return bytes.read(b,off,len);}public boolean isFinished(){return bytes.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous receipt request");}};}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
        },response);
    }
}
