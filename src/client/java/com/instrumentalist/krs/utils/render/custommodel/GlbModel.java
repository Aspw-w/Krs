package com.instrumentalist.krs.utils.render.custommodel;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GlbModel {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    public final List<Primitive> primitives = new ArrayList<>();
    public final List<Texture> textures = new ArrayList<>();

    public static final class Primitive {
        public final String meshName;
        public float[] positions;
        public float[] normals;
        public float[] uvs;
        public int[] indices;
        public int texture = -1;
        public int color = 0xFFFFFFFF;
        public boolean translucent;

        Primitive(String meshName) {
            this.meshName = meshName;
        }

        public int vertexCount() {
            return positions.length / 3;
        }
    }

    public static final class Texture {
        public final int width;
        public final int height;
        public final int[] argb;

        Texture(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }

    private JSONObject json;
    private ByteBuffer bin;
    private Path baseDir;
    private final Map<Integer, Integer> textureSlots = new HashMap<>();

    private GlbModel() {
    }

    static GlbModel empty() {
        return new GlbModel();
    }

    public static GlbModel load(Path path) throws IOException {
        GlbModel model = new GlbModel();
        model.baseDir = path.getParent();
        model.parse(Files.readAllBytes(path));
        return model;
    }

    public static GlbModel load(byte[] data) throws IOException {
        GlbModel model = new GlbModel();
        model.parse(data);
        return model;
    }

    private void parse(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 20 || buffer.getInt(0) != GLB_MAGIC)
            throw new IOException("Not a GLB file");

        int offset = 12;
        while (offset + 8 <= data.length) {
            int length = buffer.getInt(offset);
            int type = buffer.getInt(offset + 4);
            int start = offset + 8;
            if (start + length > data.length)
                throw new IOException("Truncated GLB chunk");

            if (type == CHUNK_JSON)
                json = new JSONObject(new String(data, start, length, StandardCharsets.UTF_8));
            else if (type == CHUNK_BIN && bin == null)
                bin = ByteBuffer.wrap(data, start, length).slice().order(ByteOrder.LITTLE_ENDIAN);

            offset = start + ((length + 3) & ~3);
        }

        if (json == null)
            throw new IOException("GLB has no JSON chunk");

        JSONArray scenes = json.optJSONArray("scenes");
        JSONArray nodes = json.optJSONArray("nodes");
        if (nodes == null)
            return;

        JSONArray roots;
        if (scenes != null && !scenes.isEmpty()) {
            roots = scenes.getJSONObject(Math.max(0, Math.min(json.optInt("scene", 0), scenes.length() - 1))).optJSONArray("nodes");
        } else {
            roots = new JSONArray();
            for (int i = 0; i < nodes.length(); i++)
                roots.put(i);
        }

        if (roots != null) {
            for (int i = 0; i < roots.length(); i++)
                visitNode(nodes, roots.getInt(i), new Matrix4f(), 0);
        }
    }

    private void visitNode(JSONArray nodes, int index, Matrix4f parent, int depth) throws IOException {
        if (depth > 64 || index < 0 || index >= nodes.length())
            return;

        JSONObject node = nodes.getJSONObject(index);
        Matrix4f world = new Matrix4f(parent).mul(localMatrix(node));

        if (node.has("mesh"))
            addMesh(json.getJSONArray("meshes").getJSONObject(node.getInt("mesh")), world);

        JSONArray children = node.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++)
                visitNode(nodes, children.getInt(i), world, depth + 1);
        }
    }

    private static Matrix4f localMatrix(JSONObject node) {
        JSONArray m = node.optJSONArray("matrix");
        if (m != null && m.length() == 16) {
            float[] values = new float[16];
            for (int i = 0; i < 16; i++)
                values[i] = (float) m.getDouble(i);
            return new Matrix4f().set(values);
        }

        Matrix4f local = new Matrix4f();
        JSONArray t = node.optJSONArray("translation");
        if (t != null)
            local.translate((float) t.getDouble(0), (float) t.getDouble(1), (float) t.getDouble(2));
        JSONArray r = node.optJSONArray("rotation");
        if (r != null)
            local.rotate(new Quaternionf((float) r.getDouble(0), (float) r.getDouble(1), (float) r.getDouble(2), (float) r.getDouble(3)));
        JSONArray s = node.optJSONArray("scale");
        if (s != null)
            local.scale((float) s.getDouble(0), (float) s.getDouble(1), (float) s.getDouble(2));
        return local;
    }

    private void addMesh(JSONObject mesh, Matrix4f world) throws IOException {
        JSONArray prims = mesh.optJSONArray("primitives");
        if (prims == null)
            return;

        Matrix3f normalMatrix = new Matrix3f(world).invert().transpose();
        String name = mesh.optString("name", "");

        for (int p = 0; p < prims.length(); p++) {
            JSONObject prim = prims.getJSONObject(p);
            int mode = prim.optInt("mode", 4);
            JSONObject attributes = prim.optJSONObject("attributes");
            if (attributes == null || !attributes.has("POSITION") || (mode != 4 && mode != 5 && mode != 6))
                continue;

            Primitive out = new Primitive(name);
            float[] positions = readAccessor(attributes.getInt("POSITION"));
            int count = positions.length / 3;
            Vector3f v = new Vector3f();
            for (int i = 0; i < count; i++) {
                world.transformPosition(v.set(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]));
                positions[i * 3] = v.x;
                positions[i * 3 + 1] = v.y;
                positions[i * 3 + 2] = v.z;
            }
            out.positions = positions;

            int[] indices;
            if (prim.has("indices")) {
                float[] raw = readAccessor(prim.getInt("indices"));
                indices = new int[raw.length];
                for (int i = 0; i < raw.length; i++)
                    indices[i] = (int) raw[i];
            } else {
                indices = new int[count];
                for (int i = 0; i < count; i++)
                    indices[i] = i;
            }
            out.indices = toTriangleList(indices, mode, count);

            if (attributes.has("NORMAL")) {
                float[] normals = readAccessor(attributes.getInt("NORMAL"));
                for (int i = 0; i < count; i++) {
                    normalMatrix.transform(v.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]));
                    if (v.lengthSquared() > 1.0e-12f)
                        v.normalize();
                    normals[i * 3] = v.x;
                    normals[i * 3 + 1] = v.y;
                    normals[i * 3 + 2] = v.z;
                }
                out.normals = normals;
            } else {
                out.normals = computeNormals(positions, out.indices);
            }

            out.uvs = attributes.has("TEXCOORD_0") ? readAccessor(attributes.getInt("TEXCOORD_0")) : new float[count * 2];

            if (prim.has("material"))
                applyMaterial(out, json.getJSONArray("materials").getJSONObject(prim.getInt("material")));

            if (out.indices.length >= 3)
                primitives.add(out);
        }
    }

    private void applyMaterial(Primitive out, JSONObject material) {
        out.translucent = "BLEND".equals(material.optString("alphaMode", "OPAQUE"));
        JSONObject pbr = material.optJSONObject("pbrMetallicRoughness");
        if (pbr == null)
            return;

        JSONArray factor = pbr.optJSONArray("baseColorFactor");
        if (factor != null && factor.length() == 4) {
            int r = clampColor(factor.getDouble(0));
            int g = clampColor(factor.getDouble(1));
            int b = clampColor(factor.getDouble(2));
            int a = clampColor(factor.getDouble(3));
            out.color = a << 24 | r << 16 | g << 8 | b;
        }

        JSONObject baseTexture = pbr.optJSONObject("baseColorTexture");
        if (baseTexture != null)
            out.texture = textureSlot(baseTexture.getInt("index"));
    }

    private int textureSlot(int textureIndex) {
        Integer cached = textureSlots.get(textureIndex);
        if (cached != null)
            return cached;

        int slot = -1;
        try {
            JSONObject texture = json.getJSONArray("textures").getJSONObject(textureIndex);
            int source = texture.optInt("source", -1);
            JSONObject extensions = texture.optJSONObject("extensions");
            if (extensions != null) {
                JSONObject webp = extensions.optJSONObject("EXT_texture_webp");
                if (webp != null && webp.has("source"))
                    source = webp.getInt("source");
            }

            if (source >= 0) {
                BufferedImage image = decodeImage(json.getJSONArray("images").getJSONObject(source));
                if (image != null) {
                    int w = image.getWidth();
                    int h = image.getHeight();
                    textures.add(new Texture(w, h, image.getRGB(0, 0, w, h, null, 0, w)));
                    slot = textures.size() - 1;
                }
            }
        } catch (Exception ignored) {
            slot = -1;
        }

        textureSlots.put(textureIndex, slot);
        return slot;
    }

    private BufferedImage decodeImage(JSONObject image) throws IOException {
        byte[] bytes;
        if (image.has("bufferView")) {
            JSONObject view = json.getJSONArray("bufferViews").getJSONObject(image.getInt("bufferView"));
            int offset = view.optInt("byteOffset", 0);
            bytes = new byte[view.getInt("byteLength")];
            bin.get(offset, bytes);
        } else if (image.has("uri")) {
            String uri = image.getString("uri");
            if (uri.startsWith("data:")) {
                bytes = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
            } else {
                if (baseDir == null)
                    return null;
                bytes = Files.readAllBytes(baseDir.resolve(uri));
            }
        } else {
            return null;
        }

        boolean webp = bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (!webp)
            return ImageIO.read(new ByteArrayInputStream(bytes));

        ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(stream);
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    private float[] readAccessor(int index) throws IOException {
        JSONObject accessor = json.getJSONArray("accessors").getJSONObject(index);
        int count = accessor.getInt("count");
        int components = switch (accessor.getString("type")) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT4" -> 16;
            default -> throw new IOException("Unsupported accessor type");
        };
        int componentType = accessor.getInt("componentType");
        int componentSize = switch (componentType) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw new IOException("Unsupported component type " + componentType);
        };
        boolean normalized = accessor.optBoolean("normalized", false);
        float[] out = new float[count * components];
        if (!accessor.has("bufferView"))
            return out;
        if (bin == null)
            throw new IOException("GLB has no binary chunk");

        JSONObject view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"));
        int base = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0);
        int stride = view.optInt("byteStride", 0);
        if (stride == 0)
            stride = componentSize * components;

        for (int i = 0; i < count; i++) {
            int element = base + i * stride;
            for (int c = 0; c < components; c++) {
                int at = element + c * componentSize;
                out[i * components + c] = switch (componentType) {
                    case 5120 -> normalized ? Math.max(bin.get(at) / 127.0f, -1.0f) : bin.get(at);
                    case 5121 -> normalized ? (bin.get(at) & 0xFF) / 255.0f : bin.get(at) & 0xFF;
                    case 5122 -> normalized ? Math.max(bin.getShort(at) / 32767.0f, -1.0f) : bin.getShort(at);
                    case 5123 -> normalized ? (bin.getShort(at) & 0xFFFF) / 65535.0f : bin.getShort(at) & 0xFFFF;
                    case 5125 -> (float) (bin.getInt(at) & 0xFFFFFFFFL);
                    default -> bin.getFloat(at);
                };
            }
        }
        return out;
    }

    private static int[] toTriangleList(int[] indices, int mode, int vertexCount) {
        List<Integer> out = new ArrayList<>();
        if (mode == 4) {
            for (int i = 0; i + 2 < indices.length; i += 3)
                addTriangle(out, indices[i], indices[i + 1], indices[i + 2], vertexCount);
        } else if (mode == 5) {
            for (int i = 0; i + 2 < indices.length; i++) {
                if ((i & 1) == 0)
                    addTriangle(out, indices[i], indices[i + 1], indices[i + 2], vertexCount);
                else
                    addTriangle(out, indices[i + 1], indices[i], indices[i + 2], vertexCount);
            }
        } else {
            for (int i = 1; i + 1 < indices.length; i++)
                addTriangle(out, indices[0], indices[i], indices[i + 1], vertexCount);
        }

        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = out.get(i);
        return result;
    }

    private static void addTriangle(List<Integer> out, int a, int b, int c, int vertexCount) {
        if (a == b || b == c || a == c || a >= vertexCount || b >= vertexCount || c >= vertexCount)
            return;
        out.add(a);
        out.add(b);
        out.add(c);
    }

    private static float[] computeNormals(float[] positions, int[] indices) {
        float[] normals = new float[positions.length];
        Vector3f e1 = new Vector3f();
        Vector3f e2 = new Vector3f();
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int a = indices[i] * 3, b = indices[i + 1] * 3, c = indices[i + 2] * 3;
            e1.set(positions[b] - positions[a], positions[b + 1] - positions[a + 1], positions[b + 2] - positions[a + 2]);
            e2.set(positions[c] - positions[a], positions[c + 1] - positions[a + 1], positions[c + 2] - positions[a + 2]);
            e1.cross(e2);
            for (int v : new int[]{a, b, c}) {
                normals[v] += e1.x;
                normals[v + 1] += e1.y;
                normals[v + 2] += e1.z;
            }
        }
        for (int i = 0; i < normals.length; i += 3) {
            float len = (float) Math.sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2]);
            if (len > 1.0e-6f) {
                normals[i] /= len;
                normals[i + 1] /= len;
                normals[i + 2] /= len;
            } else {
                normals[i + 1] = 1.0f;
            }
        }
        return normals;
    }

    private static int clampColor(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0);
    }
}
