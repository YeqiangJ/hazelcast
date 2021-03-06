/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.nio.tcp;

import com.hazelcast.internal.networking.InboundHandler;
import com.hazelcast.internal.networking.HandlerStatus;
import com.hazelcast.internal.networking.nio.InboundHandlerWithCounters;
import com.hazelcast.nio.Packet;
import com.hazelcast.nio.PacketIOHelper;
import com.hazelcast.util.function.Consumer;

import java.nio.ByteBuffer;

import static com.hazelcast.internal.networking.HandlerStatus.CLEAN;
import static com.hazelcast.nio.IOUtil.compactOrClear;
import static com.hazelcast.nio.Packet.FLAG_URGENT;

/**
 * The {@link InboundHandler} for member to member communication.
 *
 * It reads as many packets from the src {@link ByteBuffer} as possible, and
 * each of the Packets is send to the destination.
 *
 * @see Consumer
 * @see PacketEncoder
 */
public class PacketDecoder extends InboundHandlerWithCounters<ByteBuffer, Consumer<Packet>> {

    protected final TcpIpConnection connection;
    private final PacketIOHelper packetReader = new PacketIOHelper();

    public PacketDecoder(TcpIpConnection connection, Consumer<Packet> dst) {
        this.connection = connection;
        this.dst = dst;
    }

    @Override
    public void handlerAdded() {
        initSrcBuffer();
    }

    @Override
    public HandlerStatus onRead() throws Exception {
        src.flip();
        try {
            while (src.hasRemaining()) {
                Packet packet = packetReader.readFrom(src);
                if (packet == null) {
                    break;
                }
                onPacketComplete(packet);
            }

            return CLEAN;
        } finally {
            compactOrClear(src);
        }
    }

    protected void onPacketComplete(Packet packet) {
        if (packet.isFlagRaised(FLAG_URGENT)) {
            priorityPacketsRead.inc();
        } else {
            normalPacketsRead.inc();
        }

        packet.setConn(connection);

        dst.accept(packet);
    }
}
