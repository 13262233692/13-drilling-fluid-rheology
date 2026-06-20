package com.drilling.modbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ModbusTcpClient {

    private static final Logger log = LoggerFactory.getLogger(ModbusTcpClient.class);

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int MBAP_HEADER_SIZE = 7;
    private static final int MAX_FRAME_SIZE = 260;

    private final String host;
    private final int port;
    private final int timeoutMs;
    private final ModbusFrameParser frameParser;

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private final Object socketLock = new Object();

    public ModbusTcpClient(String host, int port) {
        this(host, port, DEFAULT_TIMEOUT_MS);
    }

    public ModbusTcpClient(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.frameParser = new ModbusFrameParser();
    }

    public void connect() throws IOException {
        synchronized (socketLock) {
            if (isConnected()) {
                return;
            }
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            log.info("Connected to Modbus device at {}:{}", host, port);
        }
    }

    public void disconnect() {
        synchronized (socketLock) {
            closeQuietly();
        }
    }

    public boolean isConnected() {
        synchronized (socketLock) {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }
    }

    public int[] readHoldingRegisters(int unitId, int startAddress, int quantity) {
        synchronized (socketLock) {
            ensureConnected();
            try {
                byte[] request = frameParser.buildReadHoldingRegistersRequest(unitId, startAddress, quantity);
                outputStream.write(request);
                outputStream.flush();

                byte[] response = readResponseFrame();
                return frameParser.parseReadHoldingRegistersResponse(response);
            } catch (ModbusFrameParser.ModbusException e) {
                throw e;
            } catch (IOException e) {
                log.error("I/O error during readHoldingRegisters, closing connection", e);
                closeQuietly();
                throw new ModbusFrameParser.ModbusException("I/O error: " + e.getMessage());
            }
        }
    }

    private byte[] readResponseFrame() throws IOException {
        byte[] mbapHeader = readNBytes(MBAP_HEADER_SIZE);
        ByteBuffer headerBuffer = ByteBuffer.wrap(mbapHeader);
        headerBuffer.order(ByteOrder.BIG_ENDIAN);

        headerBuffer.getShort();
        headerBuffer.getShort();
        int length = headerBuffer.getShort() & 0xFFFF;

        int pduLength = length - 1;
        byte[] pdu = readNBytes(pduLength);

        byte[] frame = new byte[MBAP_HEADER_SIZE + pduLength];
        System.arraycopy(mbapHeader, 0, frame, 0, MBAP_HEADER_SIZE);
        System.arraycopy(pdu, 0, frame, MBAP_HEADER_SIZE, pduLength);

        return frame;
    }

    private byte[] readNBytes(int n) throws IOException {
        byte[] buffer = new byte[n];
        int totalRead = 0;
        while (totalRead < n) {
            int read = inputStream.read(buffer, totalRead, n - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream after reading " + totalRead + " of " + n + " bytes");
            }
            totalRead += read;
        }
        return buffer;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            try {
                connect();
            } catch (IOException e) {
                throw new ModbusFrameParser.ModbusException("Auto-reconnect failed: " + e.getMessage());
            }
        }
    }

    private void closeQuietly() {
        if (inputStream != null) {
            try { inputStream.close(); } catch (IOException ignored) {}
            inputStream = null;
        }
        if (outputStream != null) {
            try { outputStream.close(); } catch (IOException ignored) {}
            outputStream = null;
        }
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
    }
}
