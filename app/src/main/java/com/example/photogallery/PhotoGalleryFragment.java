package com.example.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment{
    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<Integer> mThumbnailDownloader;
    private GridLayoutManager mGridLayoutManager;
    private int mCurPage = 1;
    private int mMaxPage = 4;
    private boolean isLoading = false;
    private int mItemsPerPage = 1;
    private int mFirstItemPosition, mLastItemPosition;

    public static Fragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        new FetchItemsTask().execute();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<Integer>() {
                    @Override
                    public void onThumbnailDownloaded(Integer position, Bitmap bitmap) {
                        mPhotoRecyclerView.getAdapter().notifyItemChanged(position);
//                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
//                        photoHolder.bindDrawable(drawable);
                    }
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));

        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!isLoading && dy > 0  && mCurPage < mMaxPage && mGridLayoutManager.findLastVisibleItemPosition() >= (mItems.size()-1)){
                    Log.d(TAG, "dy: " + dy);
                    Log.d(TAG, "Fetching more items");
                    mCurPage++;
                    isLoading = true;
                    new FetchItemsTask().execute();
                }

            }
        });
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int oldSize = mItems.size();
                mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                float columnWidthInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 140, getActivity().getResources().getDisplayMetrics());
                int width = mPhotoRecyclerView.getWidth();
                int columnNumber = Math.round(width / columnWidthInPixels);
                mGridLayoutManager = new GridLayoutManager(getActivity(), columnNumber);
                mPhotoRecyclerView.setLayoutManager(mGridLayoutManager);
                //mPhotoRecyclerView.smoothScrollToPosition(oldSize);
                isLoading = false;
                setupAdapter();
            }
        });

        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int lastVisibleItem = mGridLayoutManager.findLastVisibleItemPosition();
                int firstVisibleItem = mGridLayoutManager.findFirstVisibleItemPosition();
                if (mLastItemPosition != lastVisibleItem || mFirstItemPosition != firstVisibleItem){
                    Log.d(TAG, "Showing item " + firstVisibleItem + " to " + lastVisibleItem);
                    mLastItemPosition = lastVisibleItem;
                    mFirstItemPosition = firstVisibleItem;
                    int begin = Math.max(firstVisibleItem-10, 0);
                    int end = Math.min(lastVisibleItem+10, mItems.size()-1);
                    for (int position = begin; position <=end; position++){
                        String url = mItems.get(position).getUrl();
                        if (mThumbnailDownloader.mCache.get(url) == null){
                            Log.d(TAG, "Requesting Download at position: " + position);
                            mThumbnailDownloader.queueThumbnail(position, url);
                        }
                    }
                }

                if ((!isLoading) && (dy>0) && (mCurPage < mMaxPage) && lastVisibleItem >= mItems.size() - 1){
                    Log.d(TAG, "Fetching more items");
                    new FetchItemsTask().execute();
                }
            }
        });

        if (mPhotoRecyclerView.getAdapter() == null) {
            setupAdapter();
        }
        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
        mThumbnailDownloader.clearCache();
    }

    private void setupAdapter() {
        if (isAdded()){
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable){
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder>{
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems){
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, viewGroup, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            String url = galleryItem.getUrl();
            Bitmap bitmap = mThumbnailDownloader.mCache.get(url);
            if (bitmap == null){
                Drawable placeholder = getResources().getDrawable(R.drawable.bill_up_close);
                photoHolder.bindDrawable(placeholder);
            } else {
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                photoHolder.bindDrawable(drawable);
            }
            //mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>>{

        @Override
        protected List<GalleryItem> doInBackground(Void... params) {
//            try{
//                String result = new FlickrFetchr().getUrlString("https://www.bignerdranch.com");
//                Log.i(TAG, "Fetched contents of URL: " + result);
//            } catch (IOException ioe){
//                Log.e(TAG, "Failed to fetch URL: ", ioe);
//            }
            isLoading = true;
            return new FlickrFetchr().fetchItems(mCurPage);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
//            mItems = items;
//            setupAdapter();
            //isLoading = false;
            Log.i("SIZE", "size: " + mItems.size());
            if ( mItems.size() == 0) {
                mItems.addAll(items);
                setupAdapter();
            } else {
                mItems.addAll(items);
                mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        isLoading = false;
                    }
                });
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            }
        }
    }
}
