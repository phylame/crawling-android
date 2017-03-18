package pw.phylame.crawling.util;

import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import pw.phylame.commons.function.Function;

@RequiredArgsConstructor
public class GlideListenerAdapter implements RequestListener<Object, GlideDrawable> {
    @NonNull
    private final Function<GlideDrawable, Boolean> action;

    @Override
    public boolean onException(Exception e, Object model, Target<GlideDrawable> target, boolean isFirstResource) {
        return false;
    }

    @Override
    public boolean onResourceReady(GlideDrawable resource, Object model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
        return action.apply(resource);
    }
}
