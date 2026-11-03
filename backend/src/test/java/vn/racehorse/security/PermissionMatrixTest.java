package vn.racehorse.security;

import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the {@link Permission} enum identical to the approved matrix in ba/09-permission-matrix.md (TECH-007).
 * Column order there: Head Trainer, Veterinarian, Groom, Horse Owner, Club Manager. A cell starting with "no"
 * (for example "no (see PERM-021)") is denied.
 */
class PermissionMatrixTest {

    private static final List<Role> COLUMNS = List.of(
            Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER, Role.CLUB_MANAGER);

    @Test
    void enumMatchesApprovedMatrix() throws IOException {
        Map<String, Set<Role>> approved = readMatrix();
        for (Permission permission : Permission.values()) {
            assertThat(approved).as("row %s exists in ba/09", permission.baId()).containsKey(permission.baId());
            assertThat(permission.allowedRoles())
                    .as("roles for %s (%s)", permission, permission.baId())
                    .isEqualTo(approved.get(permission.baId()));
        }
    }

    private static Map<String, Set<Role>> readMatrix() throws IOException {
        Path file = locate();
        Map<String, Set<Role>> rows = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.matches("^\\| PERM-\\d+ \\|.*")) {
                continue;
            }
            String[] cells = line.substring(1).split("\\|");
            String id = cells[0].trim();
            Set<Role> allowed = EnumSet.noneOf(Role.class);
            for (int i = 0; i < COLUMNS.size(); i++) {
                String cell = cells[2 + i].trim().toLowerCase();
                if (!cell.startsWith("no") && !cell.contains("open")) {
                    allowed.add(COLUMNS.get(i));
                }
            }
            rows.put(id, allowed);
        }
        return rows;
    }

    private static Path locate() {
        for (Path candidate : List.of(Path.of("../ba/09-permission-matrix.md"), Path.of("ba/09-permission-matrix.md"))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("ba/09-permission-matrix.md not found");
    }
}
