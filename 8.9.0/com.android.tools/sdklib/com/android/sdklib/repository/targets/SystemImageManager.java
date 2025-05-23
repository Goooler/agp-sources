package com.android.sdklib.repository.targets;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.PackageParserUtils;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.SysImgFactory;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code SystemImageManager} finds {@link SystemImage}s in the sdk, using a {@link RepoManager}
 */
public class SystemImageManager {

    private final RepoManager mRepoManager;

    /**
     * Used to validate ABI types.
     */
    private final DetailsTypes.SysImgDetailsType mValidator;

    public static final String SYS_IMG_NAME = "system.img";

    /**
     * How far down the directory hierarchy we'll search for system images (starting from a
     * package root).
     */
    private static final int MAX_DEPTH = 4;

    /**
     * Map of packages to the images they contain
     */
    private Multimap<LocalPackage, SystemImage> mPackageToImage;

    /** Map of directories containing {@code system.img} files to {@link SystemImage}s. */
    private Map<Path, SystemImage> mPathToImage;

    /**
     * Map of tag, version, and vendor to set of system image, for convenient lookup.
     */
    private Table<IdDisplay, AndroidVersion, Multimap<IdDisplay, SystemImage>> mValuesToImage;

    /**
     * Create a new {@link SystemImageManager} using the given {@link RepoManager}.<br>
     * {@code factory} is used to enable validation.
     */
    public SystemImageManager(@NonNull RepoManager mgr, @NonNull SysImgFactory factory) {
        mRepoManager = mgr;
        mValidator = factory.createSysImgDetailsType();
    }

    /**
     * Gets all the {@link SystemImage}s.
     */
    @NonNull
    public Collection<SystemImage> getImages() {
        if (mPackageToImage == null) {
            init();
        }
        return mPackageToImage.values();
    }

    /**
     * Gets a map from all our {@link SystemImage}s to their containing {@link LocalPackage}s.
     */
    public Multimap<LocalPackage, SystemImage> getImageMap() {
        if (mPackageToImage == null) {
            init();
        }
        return mPackageToImage;
    }

    /**
     * Lookup all the {@link SystemImage} with the given property values.
     */
    @NonNull
    public Collection<SystemImage> lookup(@NonNull IdDisplay tag, @NonNull AndroidVersion version,
            @Nullable IdDisplay vendor) {
        if (mValuesToImage == null) {
            init();
        }
        Multimap<IdDisplay, SystemImage> m = mValuesToImage.get(tag, version);
        return m == null ? ImmutableList.of() : m.get(vendor);
    }

    private void init() {
        Multimap<LocalPackage, SystemImage> images = buildImageMap();
        Table<IdDisplay, AndroidVersion, Multimap<IdDisplay, SystemImage>> valuesToImage =
                HashBasedTable.create();
        Map<Path, SystemImage> pathToImages = Maps.newHashMap();
        for (SystemImage img : images.values()) {
            IdDisplay vendor = img.getAddonVendor();
            IdDisplay tag = img.getTag();
            AndroidVersion version = img.getAndroidVersion();
            Multimap<IdDisplay, SystemImage> vendorImageMap = valuesToImage.get(tag, version);
            if (vendorImageMap == null) {
                vendorImageMap = HashMultimap.create();
                valuesToImage.put(tag, version, vendorImageMap);
            }
            vendorImageMap.put(vendor, img);
            pathToImages.put(img.getLocation(), img);
        }
        mValuesToImage = valuesToImage;
        mPackageToImage = images;
        mPathToImage = pathToImages;
    }

    @NonNull
    private Multimap<LocalPackage, SystemImage> buildImageMap() {
        Multimap<LocalPackage, SystemImage> result = HashMultimap.create();
        Map<AndroidVersion, Path> platformSkins = Maps.newHashMap();
        Collection<? extends LocalPackage> packages =
                mRepoManager.getPackages().getLocalPackages().values();
        for (LocalPackage p : packages) {
            if (p.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType) {
                Path skinDir = p.getLocation().resolve(SdkConstants.FD_SKINS);
                if (CancellableFileIo.exists(skinDir)) {
                    platformSkins.put(((DetailsTypes.PlatformDetailsType) p.getTypeDetails())
                            .getAndroidVersion(), skinDir);
                }
            }
        }
        for (LocalPackage p : packages) {
            TypeDetails typeDetails = p.getTypeDetails();
            if (typeDetails instanceof DetailsTypes.SysImgDetailsType ||
                    typeDetails instanceof DetailsTypes.PlatformDetailsType ||
                    typeDetails instanceof DetailsTypes.AddonDetailsType) {
                collectImages(p.getLocation(), p, platformSkins, result);
            }
        }
        return result;
    }

    private void collectImages(
            Path dir,
            LocalPackage p,
            Map<AndroidVersion, Path> platformSkins,
            Multimap<LocalPackage, SystemImage> collector) {
        try {
            CancellableFileIo.walkFileTree(
                    dir,
                    ImmutableSet.of(),
                    MAX_DEPTH,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {
                            String name = dir.getFileName().toString();
                            if (name.equals(SdkConstants.FD_DATA)
                                    || name.equals(SdkConstants.FD_SAMPLES)
                                    || name.equals(SdkConstants.FD_SKINS)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            // Instead of just f.getName().equals, we first check
                            // f.getPath().endsWith,
                            // because getPath() is a simpler getter whereas getName() computes a
                            // new
                            // string on each call
                            if (file.toString().endsWith(SYS_IMG_NAME)
                                    && file.getFileName().toString().equals(SYS_IMG_NAME)) {
                                collector.put(p, createSysImg(p, file.getParent(), platformSkins));
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ignore) {
        }
    }

    private SystemImage createSysImg(
            LocalPackage p, Path dir, Map<AndroidVersion, Path> platformSkins) {
        String containingDir = dir.getFileName().toString();
        List<String> abis, translatedAbis;
        TypeDetails details = p.getTypeDetails();
        AndroidVersion version = null;
        if (details instanceof DetailsTypes.ApiDetailsType) {
            version = ((DetailsTypes.ApiDetailsType) details).getAndroidVersion();
        }
        if (details instanceof DetailsTypes.SysImgDetailsType) {
            abis = new ArrayList<>();
            translatedAbis = new ArrayList<>();
            readSysImgAbis(
                    p.getLocation(),
                    (DetailsTypes.SysImgDetailsType) details,
                    abis,
                    translatedAbis);
        } else if (mValidator.isValidAbi(containingDir)) {
            abis = Collections.singletonList(containingDir);
            translatedAbis = Collections.emptyList();
        } else {
            abis = Collections.singletonList(SdkConstants.ABI_ARMEABI);
            translatedAbis = Collections.emptyList();
        }

        IdDisplay vendor = null;
        if (details instanceof DetailsTypes.AddonDetailsType) {
            vendor = ((DetailsTypes.AddonDetailsType) details).getVendor();
        } else if (details instanceof DetailsTypes.SysImgDetailsType) {
            vendor = ((DetailsTypes.SysImgDetailsType) details).getVendor();
        }

        Path skinDir = dir.resolve(SdkConstants.FD_SKINS);
        if (CancellableFileIo.notExists(skinDir) && version != null) {
            skinDir = platformSkins.get(version);
        }
        List<Path> skins;
        if (skinDir != null) {
            skins = PackageParserUtils.parseSkinFolder(skinDir);
        } else {
            skins = ImmutableList.of();
        }
        return new SystemImage(
                dir, SystemImageTags.getTags(p), vendor, abis, translatedAbis, skins, p);
    }

    private static @Nullable String getCpuFamily(String abiString) {
        Abi abi = Abi.getEnum(abiString);
        return abi == null ? null : abi.getDisplayName();
    }

    private static void readSysImgAbis(
            Path location,
            DetailsTypes.SysImgDetailsType details,
            List<String> abis,
            List<String> translatedAbis) {
        if (details instanceof com.android.sdklib.repository.generated.sysimg.v1.SysImgDetailsType
                || details
                        instanceof
                        com.android.sdklib.repository.generated.sysimg.v2.SysImgDetailsType
                || details
                        instanceof
                        com.android.sdklib.repository.generated.sysimg.v3.SysImgDetailsType) {
            // We have an old image, so we won't get more than one ABI from the XML. Read from disk.
            // We also know that there shouldn't be any unknown ABIs (they would use new XML).
            List<String> allAbis = SystemImage.readAbisFromBuildProps(location);
            if (allAbis != null && !allAbis.isEmpty()) {
                // Look at the architecture of the primary ABI to determine whether others are
                // translated or not.
                String primaryCpuFamily = getCpuFamily(allAbis.get(0));
                if (primaryCpuFamily != null) {
                    for (String abi : allAbis) {
                        if (getCpuFamily(abi).equals(primaryCpuFamily)) {
                            abis.add(abi);
                        } else {
                            translatedAbis.add(abi);
                        }
                    }
                    return;
                }
            }
        }
        abis.addAll(details.getAbis());
        translatedAbis.addAll(details.getTranslatedAbis());
    }

    @Nullable
    public ISystemImage getImageAt(@NonNull Path imageDir) {
        if (mPathToImage == null) {
            init();
        }
        return mPathToImage.get(imageDir);
    }

    public void clearCache() {
        mPackageToImage = null;
        mPathToImage = null;
    }
}
