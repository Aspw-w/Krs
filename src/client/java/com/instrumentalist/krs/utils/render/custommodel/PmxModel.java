package com.instrumentalist.krs.utils.render.custommodel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PmxModel {
    public interface Resolver {
        byte[] read(String relativePath) throws IOException;
    }

    public final GlbModel mesh = GlbModel.empty();
    public final List<String> boneNames = new ArrayList<>();
    public final List<float[]> bonePositions = new ArrayList<>();
    public final List<Integer> boneParents = new ArrayList<>();
    public final List<String> texturePaths = new ArrayList<>();
    public final List<String> primitiveTexturePaths = new ArrayList<>();
    public final List<int[]> vertexBones = new ArrayList<>();
    public final List<float[]> vertexWeights = new ArrayList<>();

    private ByteBuffer in;
    private Charset charset;
    private int additionalUv;
    private int vertexIndexSize, textureIndexSize, materialIndexSize, boneIndexSize;

    private PmxModel() {
    }

    public static PmxModel load(byte[] data, Resolver resolver) throws IOException {
        PmxModel model = new PmxModel();
        model.parse(data, resolver);
        return model;
    }

    private void parse(byte[] data, Resolver resolver) throws IOException {
        in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 16 || in.get() != 'P' || in.get() != 'M' || in.get() != 'X' || in.get() != ' ')
            throw new IOException("Not a PMX file");
        in.getFloat();
        int globals = in.get() & 0xFF;
        byte[] g = new byte[globals];
        in.get(g);
        charset = g[0] == 0 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8;
        additionalUv = g[1];
        vertexIndexSize = g[2];
        textureIndexSize = g[3];
        materialIndexSize = g[4];
        boneIndexSize = g[5];

        text();
        text();
        text();
        text();

        int vertexCount = in.getInt();
        float[] pos = new float[vertexCount * 3];
        float[] nrm = new float[vertexCount * 3];
        float[] uv = new float[vertexCount * 2];
        int[] bones = new int[vertexCount * 4];
        float[] weights = new float[vertexCount * 4];
        for (int v = 0; v < vertexCount; v++) {
            pos[v * 3] = in.getFloat();
            pos[v * 3 + 1] = in.getFloat();
            pos[v * 3 + 2] = -in.getFloat();
            nrm[v * 3] = in.getFloat();
            nrm[v * 3 + 1] = in.getFloat();
            nrm[v * 3 + 2] = -in.getFloat();
            uv[v * 2] = in.getFloat();
            uv[v * 2 + 1] = in.getFloat();
            in.position(in.position() + additionalUv * 16);
            int type = in.get() & 0xFF;
            switch (type) {
                case 0 -> {
                    bones[v * 4] = index(boneIndexSize);
                    weights[v * 4] = 1.0f;
                }
                case 1, 3 -> {
                    bones[v * 4] = index(boneIndexSize);
                    bones[v * 4 + 1] = index(boneIndexSize);
                    float w = in.getFloat();
                    weights[v * 4] = w;
                    weights[v * 4 + 1] = 1.0f - w;
                    if (type == 3)
                        in.position(in.position() + 36);
                }
                case 2, 4 -> {
                    for (int k = 0; k < 4; k++)
                        bones[v * 4 + k] = index(boneIndexSize);
                    for (int k = 0; k < 4; k++)
                        weights[v * 4 + k] = in.getFloat();
                }
                default -> throw new IOException("Unknown PMX weight type " + type);
            }
            in.getFloat();
        }

        int indexCount = in.getInt();
        int[] faces = new int[indexCount];
        for (int i = 0; i < indexCount; i++)
            faces[i] = vertexIndex();
        for (int i = 0; i + 2 < indexCount; i += 3) {
            int t = faces[i + 1];
            faces[i + 1] = faces[i + 2];
            faces[i + 2] = t;
        }

        int textureCount = in.getInt();
        int[] textureSlot = new int[textureCount];
        for (int i = 0; i < textureCount; i++) {
            String path = text().replace('\\', '/');
            texturePaths.add(path);
            textureSlot[i] = -1;
            try {
                byte[] bytes = resolver.read(path);
                BufferedImage image = bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) {
                    int w = image.getWidth(), h = image.getHeight();
                    mesh.textures.add(new GlbModel.Texture(w, h, image.getRGB(0, 0, w, h, null, 0, w)));
                    textureSlot[i] = mesh.textures.size() - 1;
                }
            } catch (IOException ignored) {
            }
        }

        int materialCount = in.getInt();
        int faceOffset = 0;
        for (int m = 0; m < materialCount; m++) {
            String name = text();
            text();
            float r = in.getFloat(), gr = in.getFloat(), b = in.getFloat(), a = in.getFloat();
            in.position(in.position() + 4 * 3 + 4 + 4 * 3);
            int flags = in.get() & 0xFF;
            in.position(in.position() + 16 + 4);
            int texture = index(textureIndexSize);
            index(textureIndexSize);
            in.get();
            int sharedToon = in.get() & 0xFF;
            if (sharedToon == 0)
                index(textureIndexSize);
            else
                in.get();
            text();
            int count = in.getInt();

            if (a > 0.01f && count >= 3) {
                GlbModel.Primitive prim = new GlbModel.Primitive(name);
                Map<Integer, Integer> remap = new HashMap<>();
                List<Integer> local = new ArrayList<>();
                int[] idx = new int[count];
                for (int i = 0; i < count; i++) {
                    int v = faces[faceOffset + i];
                    Integer l = remap.get(v);
                    if (l == null) {
                        l = local.size();
                        remap.put(v, l);
                        local.add(v);
                    }
                    idx[i] = l;
                }
                int n = local.size();
                prim.positions = new float[n * 3];
                prim.normals = new float[n * 3];
                prim.uvs = new float[n * 2];
                int[] vb = new int[n * 4];
                float[] vw = new float[n * 4];
                for (int l = 0; l < n; l++) {
                    int v = local.get(l);
                    System.arraycopy(pos, v * 3, prim.positions, l * 3, 3);
                    System.arraycopy(nrm, v * 3, prim.normals, l * 3, 3);
                    System.arraycopy(uv, v * 2, prim.uvs, l * 2, 2);
                    System.arraycopy(bones, v * 4, vb, l * 4, 4);
                    System.arraycopy(weights, v * 4, vw, l * 4, 4);
                }
                prim.indices = idx;
                prim.texture = texture >= 0 && texture < textureCount ? textureSlot[texture] : -1;
                prim.color = clamp(a) << 24 | clamp(r) << 16 | clamp(gr) << 8 | clamp(b);
                prim.translucent = a < 0.99f;
                mesh.primitives.add(prim);
                primitiveTexturePaths.add(texture >= 0 && texture < textureCount ? texturePaths.get(texture) : null);
                vertexBones.add(vb);
                vertexWeights.add(vw);
            }
            faceOffset += count;
        }

        int boneCount = in.getInt();
        for (int i = 0; i < boneCount; i++) {
            boneNames.add(text());
            text();
            float x = in.getFloat(), y = in.getFloat(), z = -in.getFloat();
            bonePositions.add(new float[]{x, y, z});
            boneParents.add(index(boneIndexSize));
            in.getInt();
            int flags = in.getShort() & 0xFFFF;
            if ((flags & 0x0001) != 0)
                index(boneIndexSize);
            else
                in.position(in.position() + 12);
            if ((flags & 0x0300) != 0) {
                index(boneIndexSize);
                in.getFloat();
            }
            if ((flags & 0x0400) != 0)
                in.position(in.position() + 12);
            if ((flags & 0x0800) != 0)
                in.position(in.position() + 24);
            if ((flags & 0x2000) != 0)
                in.getInt();
            if ((flags & 0x0020) != 0) {
                index(boneIndexSize);
                in.getInt();
                in.getFloat();
                int links = in.getInt();
                for (int k = 0; k < links; k++) {
                    index(boneIndexSize);
                    if (in.get() != 0)
                        in.position(in.position() + 24);
                }
            }
        }
    }

    private String text() {
        int length = in.getInt();
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, charset);
    }

    private int index(int size) {
        return switch (size) {
            case 1 -> in.get();
            case 2 -> in.getShort();
            default -> in.getInt();
        };
    }

    private int vertexIndex() {
        return switch (vertexIndexSize) {
            case 1 -> in.get() & 0xFF;
            case 2 -> in.getShort() & 0xFFFF;
            default -> in.getInt();
        };
    }

    private static int clamp(float v) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, v)) * 255.0f);
    }
}
