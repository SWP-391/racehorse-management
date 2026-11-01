package vn.racehorse.horse.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.common.ClubClock;
import vn.racehorse.horse.dto.HorsePhotoContent;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.horse.storage.PhotoStorage;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * One photo per horse, added or replaced by the Club Manager (BR-184, PERM-003). The file is checked from its
 * content, decoded and stored again as two small renditions, which also drops EXIF and GPS data (TECH-179).
 */
@Service
public class HorsePhotoService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int FULL_SIDE = 1280;
    static final int THUMB_SIDE = 320;
    /** Decoded size cap: 8000 px per side and 40 megapixels (about 160 MB as ARGB). */
    static final int MAX_SIDE = 8000;
    static final long MAX_PIXELS = 40_000_000L;

    private final HorseRepository horses;
    private final HorseService horseService;
    private final PhotoStorage storage;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public HorsePhotoService(HorseRepository horses, HorseService horseService, PhotoStorage storage,
                             AccessGuard access, AuditService audit, ClubClock clock) {
        this.horses = horses;
        this.horseService = horseService;
        this.storage = storage;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Horse put(Long horseId, MultipartFile file) {
        AccountPrincipal actor = access.require(Permission.MANAGE_HORSES);
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", HorseService.ARCHIVED);
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.invalid("PHOTO_TYPE", "file", "Chọn một ảnh JPG hoặc PNG.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.invalid("PHOTO_TOO_LARGE", "file", "Ảnh lớn hơn 5 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.invalid("PHOTO_TYPE", "file", "Không đọc được tệp ảnh.");
        }
        String extension = typeOf(bytes);
        BufferedImage image = decode(bytes);

        String fullKey = null;
        String thumbKey = null;
        try {
            fullKey = storage.store(encode(scale(image, FULL_SIDE, extension), extension), extension);
            thumbKey = storage.store(encode(scale(image, THUMB_SIDE, extension), extension), extension);
        } catch (IOException | RuntimeException e) {
            cleanup(fullKey, thumbKey);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_STORE", "Không lưu được ảnh. Vui lòng thử lại.");
        }
        String oldFull = horse.getPhotoKey();
        String oldThumb = horse.getPhotoThumbKey();
        horse.setPhoto(fullKey, thumbKey, clock.now());
        horses.save(horse);
        audit.record(actor, oldFull == null ? "HORSE_PHOTO_ADDED" : "HORSE_PHOTO_REPLACED", "HORSE", horse.getId(),
                Map.of());
        // The earlier files go only after the new row is saved, so a failure never leaves a horse without its photo.
        cleanup(oldFull, oldThumb);
        return horse;
    }

    /** Same visibility as the horse profile; a horse without a photo answers 404, never an empty image. */
    @Transactional(readOnly = true)
    public HorsePhotoContent open(Long horseId, String variant) {
        Horse horse = horseService.get(horseId);
        String key = "thumb".equals(variant) ? horse.getPhotoThumbKey() : horse.getPhotoKey();
        if (key == null) {
            throw ApiException.notFound("ảnh ngựa");
        }
        return new HorsePhotoContent(storage.open(key), key.endsWith(".png") ? "image/png" : "image/jpeg");
    }

    private static String typeOf(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        throw ApiException.invalid("PHOTO_TYPE", "file", "Chỉ nhận ảnh JPG hoặc PNG.");
    }

    /**
     * Reads the pixel size from the header and refuses before anything is decoded: a few kilobytes of PNG can
     * describe billions of pixels (BR-184, TECH-179).
     */
    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? Collections.emptyIterator() : ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw ApiException.invalid("PHOTO_TYPE", "file", "Tệp không phải là ảnh hợp lệ.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width > MAX_SIDE || height > MAX_SIDE || width * height > MAX_PIXELS) {
                    throw ApiException.invalid("PHOTO_DIMENSIONS", "file",
                            "Ảnh quá lớn (tối đa " + MAX_SIDE + " px mỗi cạnh và 40 triệu điểm ảnh).");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw ApiException.invalid("PHOTO_TYPE", "file", "Tệp không phải là ảnh hợp lệ.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw ApiException.invalid("PHOTO_TYPE", "file", "Tệp không phải là ảnh hợp lệ.");
        }
    }

    /** Fits the long side into `side` (never enlarges); JPEG has no alpha, so it is drawn on white. */
    private static BufferedImage scale(BufferedImage src, int side, String extension) {
        double ratio = Math.min(1.0, (double) side / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        boolean jpeg = "jpg".equals(extension);
        BufferedImage out = new BufferedImage(w, h, jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (jpeg) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
            }
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage image, String extension) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if ("png".equals(extension)) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private void cleanup(String... keys) {
        for (String key : keys) {
            if (key != null) {
                try {
                    storage.delete(key);
                } catch (RuntimeException ignored) {
                    // A leftover file is harmless; the row no longer points at it.
                }
            }
        }
    }
}
