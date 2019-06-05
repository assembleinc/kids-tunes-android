package com.assembleinc.kidstunes.ui.main

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.ViewGroup
import com.assembleinc.kidstunes.R

private val TAB_TITLES = arrayOf(
    R.string.top_100,
    R.string.favorites,
    R.string.account
)

val TAB_ICONS = arrayOf(
    R.drawable.baseline_bar_chart_24,
    R.drawable.baseline_favorite_24,
    R.drawable.baseline_account_circle_24
)

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(private val context: Context, fm: FragmentManager) : FragmentPagerAdapter(fm) {

    interface OnFragmentPageSelectedListener {
        fun onFragmentSelected()
        fun onFragmentUnselected()
    }

    private var onFragmentPageSelectedListener: OnFragmentPageSelectedListener? = null

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> TopSongsFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> AccountFragment.newInstance()
            else -> AccountFragment.newInstance()
        }
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        return 3
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        super.setPrimaryItem(container, position, `object`)

        if (`object` is OnFragmentPageSelectedListener) {
            if (`object` !== onFragmentPageSelectedListener) {
                onFragmentPageSelectedListener?.onFragmentUnselected()
                onFragmentPageSelectedListener = `object`
                onFragmentPageSelectedListener?.onFragmentSelected()
            }
        }
        else {
            onFragmentPageSelectedListener?.onFragmentUnselected()
            onFragmentPageSelectedListener = null
        }
    }
}