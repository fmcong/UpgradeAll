package net.xzos.upgradeall.ui.viewmodels.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_app_list.*
import kotlinx.android.synthetic.main.layout_main.*
import net.xzos.upgradeall.R
import net.xzos.upgradeall.ui.activity.MainActivity
import net.xzos.upgradeall.ui.activity.MainActivity.Companion.setNavigationItemId
import net.xzos.upgradeall.ui.viewmodels.pageradapter.AppTabSectionsPagerAdapter
import net.xzos.upgradeall.utils.IconPalette

class AppListFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_app_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MainActivity.actionBarDrawerToggle.isDrawerIndicatorEnabled = true  // 默认允许侧滑
        AppTabSectionsPagerAdapter.newInstance(groupTabs, viewPager, childFragmentManager, viewLifecycleOwner)
    }

    override fun onResume() {
        super.onResume()
        activity?.run {
            navView.setCheckedItem(R.id.app_list)
            app_logo_image_view.visibility = View.GONE
            collapsingToolbarLayout.contentScrim = getDrawable(R.color.colorPrimary)
            toolbar_backdrop_image.setBackgroundColor(IconPalette.getColorInt(R.color.colorPrimary))
            floatingActionButton.visibility = View.GONE
            addFloatingActionButton.let { fab ->
                fab.setOnClickListener {
                    setNavigationItemId(R.id.appSettingFragment)
                }
                fab.setImageDrawable(IconPalette.fabAddIcon)
                fab.backgroundTintList = ColorStateList.valueOf((IconPalette.getColorInt(R.color.bright_yellow)))
                fab.setColorFilter(IconPalette.getColorInt(R.color.light_gray))
                fab.visibility = View.VISIBLE
            }
        }
    }
}
