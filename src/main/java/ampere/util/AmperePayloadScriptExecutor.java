package ampere.util;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class AmperePayloadScriptExecutor {

    private AmperePayloadScriptExecutor() {
    }

    public static ScriptResult execute(String javaSource, Context context) {
        Context safeContext = context == null ? new Context("minecraft:empty", new byte[0], null) : context;
        String trimmed = javaSource == null ? "" : javaSource.trim();

        if (!trimmed.isEmpty()) {
            AmpereMessaging.sendPrefixed("§eNote: Java scripting is disabled. Sending raw payload bytes.");
        }

        return safeContext.result(safeContext.channel(), safeContext.rawBytes());
    }

    public record ScriptResult(String channel, byte[] bytes) {
        public ScriptResult {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public static final class Context {
        private final String channel;
        private final byte[] rawBytes;
        private final Integer commandApiValue;

        public Context(String channel, byte[] rawBytes, Integer commandApiValue) {
            this.channel = channel == null ? "minecraft:empty" : channel;
            this.rawBytes = rawBytes == null ? new byte[0] : rawBytes.clone();
            this.commandApiValue = commandApiValue;
        }

        public String channel() {
            return channel;
        }

        public Identifier channelId() {
            return AmperePayloadSupport.parseChannel(channel);
        }

        public byte[] rawBytes() {
            return rawBytes.clone();
        }

        public Integer commandApiValue() {
            return commandApiValue;
        }

        public FriendlyByteBuf copyRawBuf() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            if (rawBytes.length > 0) {
                buf.writeBytes(rawBytes);
            }
            return buf;
        }

        public FriendlyByteBuf newBuffer() {
            return new FriendlyByteBuf(Unpooled.buffer());
        }

        public byte[] toByteArray(FriendlyByteBuf buf) {
            return AmperePayloadSupport.toByteArray(buf);
        }

        public byte[] parseBytes(String text) {
            return AmperePayloadSupport.parsePayloadBytes(text);
        }

        public String toHex(byte[] bytes) {
            return AmperePayloadSupport.toHex(bytes);
        }

        public byte[] commandApiBytes(int value) {
            return AmperePayloadSupport.withCommandApiValue(rawBytes, value);
        }

        public ScriptResult result(String nextChannel, byte[] bytes) {
            return new ScriptResult(nextChannel == null || nextChannel.isBlank() ? channel : nextChannel, bytes);
        }
    }
}
