package host.stjin.anonaddy.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import host.stjin.anonaddy.BaseBottomSheetDialogFragment
import host.stjin.anonaddy.BuildConfig
import host.stjin.anonaddy.R
import host.stjin.anonaddy.databinding.BottomsheetProfileBinding
import host.stjin.anonaddy.ui.appsettings.AppSettingsActivity
import host.stjin.anonaddy.ui.domains.DomainSettingsActivity
import host.stjin.anonaddy.ui.rules.RulesSettingsActivity
import host.stjin.anonaddy.ui.usernames.UsernamesSettingsActivity
import host.stjin.anonaddy.utils.AttributeHelper
import host.stjin.anonaddy.utils.ReviewHelper
import host.stjin.anonaddy_shared.AddyIo
import host.stjin.anonaddy_shared.AddyIoApp
import host.stjin.anonaddy_shared.utils.DateTimeUtils
import java.util.Locale


class ProfileBottomDialogFragment : BaseBottomSheetDialogFragment() {


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
    }

    var updateAvailable: Boolean = false
    var permissionsRequired: Boolean = false
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private var _binding: BottomsheetProfileBinding? = null

    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetProfileBinding.inflate(inflater, container, false)
        // get the views and attach the listener
        val root = binding.root

        setInfo()
        setOnClickListeners()

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                if (data?.getBooleanExtra("hasNewSubscription", false) == true) {
                    setInfo()
                    (activity as MainActivity).refreshAllData()

                    // User has switched or purchased a subscription, this is usually a sign of a satisfied user, let's ask the user to review the app
                    activity?.let { ReviewHelper().launchReviewFlow(it) }
                }
            }
        }

        return root

    }

    override fun onResume() {
        super.onResume()

        // When this view comes into the screen, set the update text
        // The lower the check-method
        checkForUpdates()
        checkForPermissions()
        tintSettingsIcon()
        checkForHostedInstance()

    }

    private fun checkForHostedInstance() {
        if (AddyIo.isUsingHostedInstance) {
            binding.mainProfileSelectDialogManageSubscription.visibility = View.VISIBLE
        } else {
            binding.mainProfileSelectDialogManageSubscription.visibility = View.GONE
        }
    }

    private fun checkForPermissions() {
        if (permissionsRequired) {
            binding.mainProfileSelectDialogAppSettingsDesc.text =
                resources.getString(R.string.permissions_required)
        }
    }

    private fun checkForUpdates() {
        // The main activity tells the dialog if an update is available
        if (updateAvailable) {
            binding.mainProfileSelectDialogAppSettingsDesc.text =
                resources.getString(R.string.version_s_update_available, BuildConfig.VERSION_NAME)
        }

    }

    private fun tintSettingsIcon() {
        if (updateAvailable || permissionsRequired) {
            ImageViewCompat.setImageTintList(
                binding.mainProfileSelectDialogAppSettingsIcon,
                context?.let { ContextCompat.getColorStateList(it, R.color.softRed) }
            )
        } else {
            ImageViewCompat.setImageTintList(
                binding.mainProfileSelectDialogAppSettingsIcon,
                context?.let { ColorStateList.valueOf(AttributeHelper.getValueByAttr(it, R.attr.colorControlNormal)) }
            )
            binding.mainProfileSelectDialogAppSettingsDesc.text = resources.getString(R.string.version_s, BuildConfig.VERSION_NAME)
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("updateAvailable", updateAvailable)
        outState.putBoolean("permissionsRequired", permissionsRequired)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            updateAvailable = savedInstanceState.getBoolean("updateAvailable")
            permissionsRequired = savedInstanceState.getBoolean("permissionsRequired")
        }
    }

    private fun setOnClickListeners() {

        binding.mainProfileSelectDialogAppSettings.setOnClickListener {
            val intent = Intent(activity, AppSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.mainProfileSelectDialogDomainSettings.setOnClickListener {
            val intent = Intent(activity, DomainSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.mainProfileSelectDialogRules.setOnClickListener {
            val intent = Intent(activity, RulesSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.mainProfileSelectDialogUsernameSettings.setOnClickListener {
            val intent = Intent(activity, UsernamesSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.mainProfileSelectDialogAnonaddySettings.setOnClickListener {
            val url = "${AddyIo.API_BASE_URL}/settings"
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        }

        binding.mainProfileSelectDialogManageSubscription.setOnClickListener {
            if (BuildConfig.FLAVOR == "gplay") {
                val intent = Intent(activity, ManageSubscriptionActivity::class.java)
                resultLauncher.launch(intent)
            } else {
                val url = "${AddyIo.API_BASE_URL}/settings/subscription"
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(url)
                startActivity(i)
            }

        }
    }



    private fun setInfo() {
        val usernameInitials = (activity?.application as AddyIoApp).userResource.username.take(2).uppercase(Locale.getDefault())
        binding.mainProfileSelectDialogUsernameInitials.text = usernameInitials

        binding.mainProfileSelectDialogAnonaddyVersion.text =
            if (AddyIo.isUsingHostedInstance) this.resources.getString(R.string.hosted_instance) else this.resources.getString(
                R.string.self_hosted_instance_s,
                AddyIo.VERSIONSTRING
            )

        binding.mainProfileSelectDialogCardAccountname.text = (activity?.application as AddyIoApp).userResource.username

        setSubscriptionText()

        binding.mainProfileSelectDialogAppSettingsDesc.text = resources.getString(R.string.version_s, BuildConfig.VERSION_NAME)
    }

    private fun setSubscriptionText() {
        if ((activity?.application as AddyIoApp).userResource.subscription != null) {
            binding.mainProfileSelectDialogCardLL.visibility = View.VISIBLE
            binding.mainProfileSelectDialogCardSubscription.text =
                resources.getString(R.string.subscription_user, (activity?.application as AddyIoApp).userResource.subscription)
        } else {
            binding.mainProfileSelectDialogCardLL.visibility = View.GONE
        }

        if ((activity?.application as AddyIoApp).userResource.subscription_ends_at != null) {
            binding.mainProfileSelectDialogCardSubscriptionUntil.visibility = View.VISIBLE
            binding.mainProfileSelectDialogCardSubscriptionUntil.text =
                resources.getString(
                    R.string.subscription_user_until, DateTimeUtils.convertStringToLocalTimeZoneString(
                        (activity?.application as AddyIoApp).userResource.subscription_ends_at,
                        DateTimeUtils.DatetimeFormat.DATE
                    )
                )
        } else {
            binding.mainProfileSelectDialogCardSubscriptionUntil.visibility = View.GONE
        }
    }

    companion object {
        fun newInstance(): ProfileBottomDialogFragment {
            return ProfileBottomDialogFragment()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}