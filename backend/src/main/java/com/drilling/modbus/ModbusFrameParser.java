package com.drilling.modbus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ModbusFrameParser {

    private static final int MBAP_HEADER_SIZE = 7;
    private static final short PROTOCOL_ID = 0;
    private static final byte FUNCTION_READ_HOLDING_REGISTERS = 0x03;
    private static final byte FUNCTION_ERROR_MASK = (byte) 0x80;

    private int transactionId = 0;

    public synchronized byte[] buildReadHoldingRegistersRequest(int unitId, int startAddress, int quantity) {
        int pduLength = 5;
        int totalLength = MBAP_HEADER_SIZE + pduLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) transactionId);
        buffer.putShort(PROTOCOL_ID);
        buffer.putShort((short) (pduLength + 1));
        buffer.put((byte) unitId);
        buffer.put(FUNCTION_READ_HOLDING_REGISTERS);
        buffer.putShort((short) startAddress);
        buffer.putShort((short) quantity);

        transactionId = (transactionId + 1) & 0xFFFF;

        return buffer.array();
    }

    public int[] parseReadHoldingRegistersResponse(byte[] frame) {
        if (frame == null || frame.length < MBAP_HEADER_SIZE + 2) {
            throw new ModbusException("Frame too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(frame);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.getShort();
        buffer.getShort();
        short length = buffer.getShort();
        byte unitId = buffer.get();
        byte functionCode = buffer.get();

        if ((functionCode & FUNCTION_ERROR_MASK) != 0) {
            byte exceptionCode = buffer.get();
            throw new ModbusException("Modbus exception response: function=0x"
                    + String.format("%02X", functionCode)
                    + ", exceptionCode=0x"
                    + String.format("%02X", exceptionCode));
        }

        if (functionCode != FUNCTION_READ_HOLDING_REGISTERS) {
            throw new ModbusException("Unexpected function code: 0x" + String.format("%02X", functionCode));
        }

        byte byteCount = buffer.get();
        int registerCount = byteCount & 0xFF;
        int[] registers = new int[registerCount];

        for (int i = 0; i < registerCount; i++) {
            registers[i] = buffer.getShort() & 0xFFFF;
        }

        return registers;
    }

    public int getTransactionId(byte[] frame) {
        if (frame == null || frame.length < 2) {
            return -1;
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame, 0, 2);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getShort() & 0xFFFF;
    }

    public static class ModbusException extends RuntimeException {
        public ModbusException(String message) {
            super(message);
        }
    }
}
