package vn.racehorse.injury;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.racehorse.region.entity.BodyRegion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared 3D model must have, for every catalogued region, a part in the skin, muscle and skeleton layers and an
 * anchor for the marker (NFR-014, TECH-004, TECH-049). A replacement model that keeps these node names loses no mark.
 */
class HorseModelTest {

    @Test
    void modelCoversEveryRegionInEveryLayer() throws IOException {
        JsonNode gltf = readJson(locate());
        Set<String> withMesh = new HashSet<>();
        Set<String> all = new HashSet<>();
        for (JsonNode node : gltf.get("nodes")) {
            String name = node.get("name").asText();
            all.add(name);
            if (node.has("mesh")) {
                JsonNode prims = gltf.get("meshes").get(node.get("mesh").asInt()).get("primitives");
                assertThat(prims.size()).as("primitives of %s", name).isPositive();
                withMesh.add(name);
            }
        }
        for (BodyRegion region : BodyRegion.values()) {
            for (String layer : List.of("skin", "muscle", "skeleton")) {
                assertThat(withMesh).as("%s.%s has a mesh", layer, region).contains(layer + "." + region.name());
            }
            assertThat(all).as("anchor.%s exists", region).contains("anchor." + region.name());
        }
    }

    @Test
    void modelStaysWithinAMediumFileSize() throws IOException {
        assertThat(Files.size(locate())).isLessThan(12L * 1024 * 1024);
    }

    private static JsonNode readJson(Path file) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(buf.getInt(0)).as("glTF magic").isEqualTo(0x46546c67);
        int jsonLength = buf.getInt(12);
        return new ObjectMapper().readTree(new String(buf.array(), 20, jsonLength, StandardCharsets.UTF_8));
    }

    private static Path locate() {
        for (Path candidate : List.of(Path.of("../frontend/public/models/horse.glb"), Path.of("frontend/public/models/horse.glb"))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("frontend/public/models/horse.glb not found; run `npm run model` in frontend");
    }
}
