/*
 * Copyright (C) 2016 Peng fei Pan <sky@xiaopan.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch.viewfun.large;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;

import java.io.IOException;
import java.io.InputStream;

import me.xiaopan.sketch.Configuration;
import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.decode.DataSource;
import me.xiaopan.sketch.decode.DataSourceFactory;
import me.xiaopan.sketch.decode.DecodeException;
import me.xiaopan.sketch.decode.ImageDecodeUtils;
import me.xiaopan.sketch.decode.ImageOrientationCorrector;
import me.xiaopan.sketch.decode.ImageType;
import me.xiaopan.sketch.request.UriInfo;
import me.xiaopan.sketch.util.ExifInterface;
import me.xiaopan.sketch.util.SketchUtils;

/**
 * 图片碎片解码器，支持纠正图片方向
 */
public class ImageRegionDecoder {

    private final int exifOrientation;
    private Point imageSize;
    private String imageUri;
    private ImageType imageType;
    private BitmapRegionDecoder regionDecoder;

    ImageRegionDecoder(String imageUri, Point imageSize, ImageType imageType,
                       int exifOrientation, BitmapRegionDecoder regionDecoder) {
        this.imageUri = imageUri;
        this.imageSize = imageSize;
        this.imageType = imageType;
        this.exifOrientation = exifOrientation;
        this.regionDecoder = regionDecoder;
    }

    public static ImageRegionDecoder build(Context context, final String imageUri,
                                           final boolean correctImageOrientationDisabled) throws DecodeException, IOException {
        UriInfo uriInfo = UriInfo.make(imageUri);
        if (uriInfo == null || uriInfo.getScheme() == null) {
            throw new IllegalArgumentException("Unknown scheme uri: " + imageUri);
        }

        DataSource dataSource = DataSourceFactory.makeDataSource(context, uriInfo, null);

        // 读取图片尺寸和类型
        BitmapFactory.Options boundOptions = new BitmapFactory.Options();
        boundOptions.inJustDecodeBounds = true;
        ImageDecodeUtils.decodeBitmap(dataSource, boundOptions);
        Point imageSize = new Point(boundOptions.outWidth, boundOptions.outHeight);

        // 读取图片方向并根据方向改变尺寸
        Configuration configuration = Sketch.with(context).getConfiguration();
        ImageOrientationCorrector orientationCorrector = configuration.getImageOrientationCorrector();
        int exifOrientation = ExifInterface.ORIENTATION_UNDEFINED;
        if (!correctImageOrientationDisabled) {
            exifOrientation = orientationCorrector.readExifOrientation(boundOptions.outMimeType, dataSource);
        }
        orientationCorrector.rotateSize(imageSize, exifOrientation);

        InputStream inputStream = null;
        BitmapRegionDecoder regionDecoder;
        try {
            inputStream = dataSource.getInputStream();
            regionDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
        } finally {
            SketchUtils.close(inputStream);
        }

        ImageType imageType = ImageType.valueOfMimeType(boundOptions.outMimeType);

        return new ImageRegionDecoder(imageUri, imageSize, imageType, exifOrientation, regionDecoder);
    }

    @SuppressWarnings("unused")
    public Point getImageSize() {
        return imageSize;
    }

    public ImageType getImageType() {
        return imageType;
    }

    @SuppressWarnings("unused")
    public String getImageUri() {
        return imageUri;
    }

    public int getExifOrientation() {
        return exifOrientation;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    public boolean isReady() {
        return regionDecoder != null && !regionDecoder.isRecycled();
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    public void recycle() {
        if (isReady()) {
            regionDecoder.recycle();
            regionDecoder = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    public Bitmap decodeRegion(Rect srcRect, BitmapFactory.Options options) {
        if (isReady()) {
            return regionDecoder.decodeRegion(srcRect, options);
        } else {
            return null;
        }
    }
}
