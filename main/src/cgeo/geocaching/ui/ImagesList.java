package cgeo.geocaching.ui;

import cgeo.geocaching.R;
import cgeo.geocaching.apps.navi.NavigationAppFactory;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.models.Image;
import cgeo.geocaching.network.HtmlImage;
import cgeo.geocaching.storage.LocalStorage;
import cgeo.geocaching.utils.Log;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.Nullable;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.support.annotation.StringRes;
import android.text.Html;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.LinkedList;

import butterknife.ButterKnife;
import rx.Subscription;
import rx.android.app.AppObservable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

public class ImagesList {

    private BitmapDrawable currentDrawable;
    private Image currentImage;

    public enum ImageType {
        LogImages(R.string.cache_log_images_title), SpoilerImages(R.string.cache_spoiler_images_title);

        @StringRes private final int titleResId;

        ImageType(@StringRes final int title) {
            this.titleResId = title;
        }

        @StringRes
        public int getTitle() {
            return titleResId;
        }
    }

    private LayoutInflater inflater = null;
    private final Activity activity;
    // We could use a Set here, but we will insert no duplicates, so there is no need to check for uniqueness.
    private final Collection<Bitmap> bitmaps = new LinkedList<>();
    /**
     * map image view id to image
     */
    private final SparseArray<Image> images = new SparseArray<>();
    private final String geocode;
    private LinearLayout imagesView;

    public ImagesList(final Activity activity, final String geocode) {
        this.activity = activity;
        this.geocode = geocode;
        inflater = activity.getLayoutInflater();
    }

    /**
     * Load images into a view.
     *
     * @param parentView
     *            a view to load the images into
     * @param images
     *            the images to load
     * @return a subscription which, when unsubscribed, interrupts the loading and clears up resources
     */
    public Subscription loadImages(final View parentView, final Collection<Image> images) {
        // Start with a fresh subscription because of this method can be called several times if the
        // enclosing activity is stopped/restarted.
        final CompositeSubscription subscriptions = new CompositeSubscription(Subscriptions.create(new Action0() {
            @Override
            public void call() {
                removeAllViews();
            }
        }));

        imagesView = ButterKnife.findById(parentView, R.id.spoiler_list);

        final HtmlImage imgGetter = new HtmlImage(geocode, true, false, false);

        for (final Image img : images) {
            final LinearLayout rowView = (LinearLayout) inflater.inflate(R.layout.cache_image_item, imagesView, false);
            assert rowView != null;

            if (StringUtils.isNotBlank(img.getTitle())) {
                final TextView titleView = ButterKnife.findById(rowView, R.id.title);
                titleView.setText(Html.fromHtml(img.getTitle()));
                rowView.findViewById(R.id.titleLayout).setVisibility(View.VISIBLE);
            }

            if (StringUtils.isNotBlank(img.getDescription())) {
                final TextView descView = ButterKnife.findById(rowView, R.id.description);
                descView.setText(Html.fromHtml(img.getDescription()), TextView.BufferType.SPANNABLE);
                descView.setVisibility(View.VISIBLE);
            }

            final RelativeLayout imageView = (RelativeLayout) inflater.inflate(R.layout.image_item, rowView, false);
            assert imageView != null;
            subscriptions.add(AppObservable.bindActivity(activity, imgGetter.fetchDrawable(img.getUrl())).subscribe(new Action1<BitmapDrawable>() {
                @Override
                public void call(final BitmapDrawable image) {
                    display(imageView, image, img, rowView);
                }
            }));
            rowView.addView(imageView);
            imagesView.addView(rowView);
        }

        return subscriptions;
    }

    private void display(final RelativeLayout imageViewLayout, final BitmapDrawable image, final Image img, final LinearLayout view) {
        final ImageView imageView = (ImageView) imageViewLayout.findViewById(R.id.map_image);
        if (image != null) {
            bitmaps.add(image.getBitmap());

            final Rect bounds = image.getBounds();

            imageView.setImageResource(R.drawable.image_not_loaded);
            imageView.setClickable(true);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    viewImageInStandardApp(img, image);
                }
            });
            activity.registerForContextMenu(imageView);
            imageView.setImageDrawable(image);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setLayoutParams(new RelativeLayout.LayoutParams(bounds.width(), bounds.height()));

            view.findViewById(R.id.progress_bar).setVisibility(View.GONE);

            imageView.setId(image.hashCode());
            addGeoOverlay(imageViewLayout, img);
            images.put(imageView.getId(), img);

            view.invalidate();
        }
    }

    @Nullable
    private GeoLocation getImageLocation(final Image image) {
        try {
            final File file = LocalStorage.getStorageFile(geocode, image.getUrl(), true, false);
            final Metadata metadata = ImageMetadataReader.readMetadata(file);
            final Collection<GpsDirectory> gpsDirectories = metadata.getDirectoriesOfType(GpsDirectory.class);
            if (gpsDirectories == null) {
                return null;
            }

            for (final GpsDirectory gpsDirectory : gpsDirectories) {
                // Try to read out the location, making sure it's non-zero
                final GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                if (geoLocation != null && !geoLocation.isZero()) {
                    return geoLocation;
                }
            }
        } catch (final Exception e) {
            Log.e("ImagesList.addGeoOverlay", e);
        }
        return null;
    }

    private void addGeoOverlay(final RelativeLayout imageViewLayout, final Image img) {
        final GeoLocation geoLocation = getImageLocation(img);
        if (geoLocation != null) {
            final ImageView geoOverlay = (ImageView) imageViewLayout.findViewById(R.id.geo_overlay);
            geoOverlay.setVisibility(View.VISIBLE);
            geoOverlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View wpNavView) {
                    final Geopoint gpt = new Geopoint(geoLocation.getLatitude(), geoLocation.getLongitude());
                    wpNavView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View v) {
                            NavigationAppFactory.startDefaultNavigationApplication(1, activity, gpt);
                        }
                    });
                    wpNavView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(final View v) {
                            NavigationAppFactory.startDefaultNavigationApplication(2, activity, gpt);
                            return true;
                        }
                    });
                }
            });
        }
    }

    private void removeAllViews() {
        for (final Bitmap b : bitmaps) {
            b.recycle();
        }
        bitmaps.clear();
        images.clear();

        imagesView.removeAllViews();
    }

    public void onCreateContextMenu(final ContextMenu menu, final View v) {
        assert v instanceof ImageView;
        activity.getMenuInflater().inflate(R.menu.images_list_context, menu);
        final Resources res = activity.getResources();
        menu.setHeaderTitle(res.getString(R.string.cache_image));
        final ImageView view = (ImageView) v;
        currentDrawable = (BitmapDrawable) view.getDrawable();
        currentImage = images.get(view.getId());
    }

    public boolean onContextItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.image_open_file:
                viewImageInStandardApp(currentImage, currentDrawable);
                return true;
            case R.id.image_open_browser:
                if (currentImage != null) {
                    currentImage.openInBrowser(activity);
                }
                return true;
            default:
                return false;
        }
    }

    private static String mimeTypeForUrl(final String url) {
        return StringUtils.defaultString(MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url)), "image/*");
    }

    private static File saveToTemporaryJPGFile(final BitmapDrawable image) throws FileNotFoundException {
        final File file = LocalStorage.getStorageFile(HtmlImage.SHARED, "temp.jpg", false, true);
        BufferedOutputStream stream = null;
        try {
            stream = new BufferedOutputStream(new FileOutputStream(file));
            image.getBitmap().compress(CompressFormat.JPEG, 100, stream);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        file.deleteOnExit();
        return file;
    }

    private void viewImageInStandardApp(final Image img, final BitmapDrawable image) {
        try {
            final Intent intent = new Intent().setAction(android.content.Intent.ACTION_VIEW);
            final File file = img.isLocalFile() ? img.localFile() : LocalStorage.getStorageFile(geocode, img.getUrl(), true, true);
            if (file.exists()) {
                intent.setDataAndType(Uri.fromFile(file), mimeTypeForUrl(img.getUrl()));
            } else {
                intent.setDataAndType(Uri.fromFile(saveToTemporaryJPGFile(image)), "image/jpeg");
            }
            activity.startActivity(intent);
        } catch (final Exception e) {
            Log.e("ImagesList.viewImageInStandardApp", e);
        }
    }

}
