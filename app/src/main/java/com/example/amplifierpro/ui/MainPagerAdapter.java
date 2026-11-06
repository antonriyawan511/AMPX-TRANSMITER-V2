package com.example.amplifierpro.ui;

import com.example.amplifierpro.R;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return new DummyFragment();
        if (position == 1) return new EqualizerFragment();
        if (position == 3) return new AudioEffectsFragment();
        return new MainFragment();
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
