package host.stjin.anonaddy.ui.usernames

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import host.stjin.anonaddy.R
import host.stjin.anonaddy.adapter.UsernameAdapter
import host.stjin.anonaddy.databinding.FragmentUsernameSettingsBinding
import host.stjin.anonaddy.ui.MainActivity
import host.stjin.anonaddy.ui.usernames.manage.ManageUsernamesActivity
import host.stjin.anonaddy.utils.MarginItemDecoration
import host.stjin.anonaddy.utils.MaterialDialogHelper
import host.stjin.anonaddy.utils.ScreenSizeUtils
import host.stjin.anonaddy.utils.SnackbarHelper
import host.stjin.anonaddy_shared.AddyIoApp
import host.stjin.anonaddy_shared.NetworkHelper
import host.stjin.anonaddy_shared.managers.SettingsManager
import host.stjin.anonaddy_shared.models.UserResource
import host.stjin.anonaddy_shared.models.Usernames
import host.stjin.anonaddy_shared.utils.LoggingHelper
import kotlinx.coroutines.launch

class UsernamesSettingsFragment : Fragment(), AddUsernameBottomDialogFragment.AddUsernameBottomDialogListener {

    private var usernames: ArrayList<Usernames>? = null
    private var networkHelper: NetworkHelper? = null
    private var encryptedSettingsManager: SettingsManager? = null
    private var OneTimeRecyclerViewActions: Boolean = true

    private val addUsernameFragment: AddUsernameBottomDialogFragment = AddUsernameBottomDialogFragment.newInstance()

    companion object {
        fun newInstance() = UsernamesSettingsFragment()
    }


    private var _binding: FragmentUsernameSettingsBinding? = null

    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsernameSettingsBinding.inflate(inflater, container, false)
        val root = binding.root

        encryptedSettingsManager = SettingsManager(true, requireContext())
        networkHelper = NetworkHelper(requireContext())

        // Set stats right away, update later
        setStats()

        setOnClickListener()
        setUsernamesRecyclerView()
        getDataFromWeb(savedInstanceState)

        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val gson = Gson()
        val json = gson.toJson(usernames)
        outState.putString("usernames", json)
    }


    private fun setOnClickListener() {
        binding.fragmentUsernameSettingsAddUsername.setOnClickListener {
            if (!addUsernameFragment.isAdded) {
                addUsernameFragment.show(
                    childFragmentManager,
                    "addUsernameFragment"
                )
            }
        }
    }

    fun getDataFromWeb(savedInstanceState: Bundle?) {
        // Get the latest data in the background, and update the values when loaded
        lifecycleScope.launch {
            if (savedInstanceState != null) {
                setStats()

                val usernamesJson = savedInstanceState.getString("usernames")
                if (!usernamesJson.isNullOrEmpty() && usernamesJson != "null") {
                    val gson = Gson()

                    val myType = object : TypeToken<ArrayList<Usernames>>() {}.type
                    val list = gson.fromJson<ArrayList<Usernames>>(usernamesJson, myType)
                    setUsernamesAdapter(list)
                } else {
                    // usernamesJson could be null when an embedded activity is opened instantly
                    getUserResource()
                    getAllUsernamesAndSetView()
                }

            } else {
                getUserResource()
                getAllUsernamesAndSetView()
            }

        }
    }

    private suspend fun getUserResource() {
        networkHelper?.getUserResource { user: UserResource?, result: String? ->
            if (user != null) {
                (activity?.application as AddyIoApp).userResource = user
                // Update stats
                setStats()
            } else {
                if (requireContext().resources.getBoolean(R.bool.isTablet)) {
                    SnackbarHelper.createSnackbar(
                        requireContext(),
                        resources.getString(R.string.error_obtaining_user) + "\n" + result,
                        (activity as MainActivity).findViewById(R.id.main_container),
                        LoggingHelper.LOGFILES.DEFAULT
                    ).show()
                } else {
                    SnackbarHelper.createSnackbar(
                        requireContext(),
                        resources.getString(R.string.error_obtaining_user) + "\n" + result,
                        (activity as UsernamesSettingsActivity).findViewById(R.id.activity_username_settings_CL),
                        LoggingHelper.LOGFILES.DEFAULT
                    ).show()
                }
            }
        }
    }

    private fun setStats() {
        binding.fragmentUsernameSettingsRLCountText.text =
            resources.getString(
                R.string.you_ve_used_d_out_of_d_usernames,
                (activity?.application as AddyIoApp).userResource.username_count,
                if ((activity?.application as AddyIoApp).userResource.subscription != null) (activity?.application as AddyIoApp).userResource.username_limit else this.resources.getString(
                    R.string.unlimited
                )
            )

        // If userResource.subscription == null, that means that the user has no subscription (thus a self-hosted instance without limits)
        if ((activity?.application as AddyIoApp).userResource.subscription != null) {
            binding.fragmentUsernameSettingsAddUsername.isEnabled =
                (activity?.application as AddyIoApp).userResource.username_count < (activity?.application as AddyIoApp).userResource.username_limit
        } else {
            binding.fragmentUsernameSettingsAddUsername.isEnabled = true
        }
    }

    private fun setUsernamesRecyclerView() {
        binding.fragmentUsernameSettingsAllUsernamesRecyclerview.apply {
            if (OneTimeRecyclerViewActions) {
                OneTimeRecyclerViewActions = false

                shimmerItemCount = encryptedSettingsManager?.getSettingsInt(SettingsManager.PREFS.BACKGROUND_SERVICE_CACHE_USERNAME_COUNT, 2) ?: 2
                shimmerLayoutManager = GridLayoutManager(requireContext(), ScreenSizeUtils.calculateNoOfColumns(context))
                layoutManager = GridLayoutManager(requireContext(), ScreenSizeUtils.calculateNoOfColumns(context))

                addItemDecoration(MarginItemDecoration(this.resources.getDimensionPixelSize(R.dimen.recyclerview_margin)))

                val resId: Int = R.anim.layout_animation_fall_down
                val animation = AnimationUtils.loadLayoutAnimation(context, resId)
                layoutAnimation = animation

                showShimmer()
            }
        }
    }

    private lateinit var usernamesAdapter: UsernameAdapter
    private suspend fun getAllUsernamesAndSetView() {
        binding.fragmentUsernameSettingsAllUsernamesRecyclerview.apply {
            networkHelper?.getAllUsernames { list, error ->
                // Sorted by created_at automatically
                //list?.sortByDescending { it.emails_forwarded }

                // Check if there are new usernames since the latest list
                // If the list is the same, just return and don't bother re-init the layoutmanager
                if (::usernamesAdapter.isInitialized && list == usernamesAdapter.getList()) {
                    return@getAllUsernames
                }

                if (list != null) {
                    setUsernamesAdapter(list)
                } else {

                    if (requireContext().resources.getBoolean(R.bool.isTablet)) {
                        SnackbarHelper.createSnackbar(
                            requireContext(),
                            this@UsernamesSettingsFragment.resources.getString(R.string.error_obtaining_usernames) + "\n" + error,
                            (activity as MainActivity).findViewById(R.id.main_container),
                            LoggingHelper.LOGFILES.DEFAULT
                        ).show()
                    } else {
                        SnackbarHelper.createSnackbar(
                            requireContext(),
                            this@UsernamesSettingsFragment.resources.getString(R.string.error_obtaining_usernames) + "\n" + error,
                            (activity as UsernamesSettingsActivity).findViewById(R.id.activity_username_settings_CL),
                            LoggingHelper.LOGFILES.DEFAULT
                        ).show()
                    }

                    // Show error animations
                    binding.fragmentUsernameSettingsLL1.visibility = View.GONE
                    binding.animationFragment.playAnimation(false, R.drawable.ic_loading_logo_error)
                }
                hideShimmer()
            }

        }

    }

    private fun setUsernamesAdapter(list: java.util.ArrayList<Usernames>) {
        binding.fragmentUsernameSettingsAllUsernamesRecyclerview.apply {
            usernames = list
            if (list.size > 0) {
                binding.fragmentUsernameSettingsNoUsernames.visibility = View.GONE
            } else {
                binding.fragmentUsernameSettingsNoUsernames.visibility = View.VISIBLE
            }

            // Set the count of aliases so that the shimmerview looks better next time
            encryptedSettingsManager?.putSettingsInt(SettingsManager.PREFS.BACKGROUND_SERVICE_CACHE_USERNAME_COUNT, list.size)


            usernamesAdapter = UsernameAdapter(list)
            usernamesAdapter.setClickListener(object : UsernameAdapter.ClickListener {

                override fun onClickSettings(pos: Int, aView: View) {
                    val intent = Intent(context, ManageUsernamesActivity::class.java)
                    intent.putExtra("username_id", list[pos].id)
                    resultLauncher.launch(intent)
                }


                override fun onClickDelete(pos: Int, aView: View) {
                    deleteUsername(list[pos].id, context)
                }

            })
            adapter = usernamesAdapter

            binding.animationFragment.stopAnimation()
            //binding.fragmentUsernameSettingsNSV.animate().alpha(1.0f) -> Do not animate as there is a shimmerview

        }
    }

    var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // There are no request codes
            val data: Intent? = result.data
            if (data?.getBooleanExtra("shouldRefresh", false) == true) {
                getDataFromWeb(null)
            }
        }
    }


    private lateinit var deleteUsernameSnackbar: Snackbar
    private fun deleteUsername(id: String, context: Context) {
        MaterialDialogHelper.showMaterialDialog(
            context = requireContext(),
            title = resources.getString(R.string.delete_username),
            message = resources.getString(R.string.delete_username_desc_confirm),
            icon = R.drawable.ic_trash,
            neutralButtonText = resources.getString(R.string.cancel),
            positiveButtonText = resources.getString(R.string.delete),
            positiveButtonAction = {

                deleteUsernameSnackbar = if (requireContext().resources.getBoolean(R.bool.isTablet)) {
                    SnackbarHelper.createSnackbar(
                        requireContext(),
                        this.resources.getString(R.string.deleting_username),
                        (activity as MainActivity).findViewById(R.id.main_container),
                        length = Snackbar.LENGTH_INDEFINITE
                    )
                } else {
                    SnackbarHelper.createSnackbar(
                        requireContext(),
                        this.resources.getString(R.string.deleting_username),
                        (activity as UsernamesSettingsActivity).findViewById(R.id.activity_username_settings_CL),
                        length = Snackbar.LENGTH_INDEFINITE
                    )
                }
                deleteUsernameSnackbar.show()

                lifecycleScope.launch {
                    deleteUsernameHttpRequest(id, context)
                }
            }
        ).show()
    }

    private suspend fun deleteUsernameHttpRequest(id: String, context: Context) {
        networkHelper?.deleteUsername({ result ->
            if (result == "204") {
                deleteUsernameSnackbar.dismiss()
                getDataFromWeb(null)
            } else {


                if (requireContext().resources.getBoolean(R.bool.isTablet)) {
                    SnackbarHelper.createSnackbar(
                        requireContext(),
                        context.resources.getString(R.string.s_s, context.resources.getString(R.string.error_deleting_username), result),
                        (activity as MainActivity).findViewById(R.id.main_container),
                        LoggingHelper.LOGFILES.DEFAULT
                    ).show()
                } else {
                    SnackbarHelper.createSnackbar(
                        requireContext(),
                        context.resources.getString(R.string.s_s, context.resources.getString(R.string.error_deleting_username), result),
                        (activity as UsernamesSettingsActivity).findViewById(R.id.activity_username_settings_CL),
                        LoggingHelper.LOGFILES.DEFAULT
                    ).show()
                }
            }
        }, id)
    }

    override fun onAdded() {
        addUsernameFragment.dismissAllowingStateLoss()
        // Get the latest data in the background, and update the values when loaded
        getDataFromWeb(null)
    }

}