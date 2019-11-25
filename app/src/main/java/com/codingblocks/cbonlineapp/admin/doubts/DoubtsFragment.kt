package com.codingblocks.cbonlineapp.admin.doubts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.util.Components
import com.codingblocks.cbonlineapp.util.UNAUTHORIZED
import com.codingblocks.cbonlineapp.util.extensions.observer
import com.codingblocks.onlineapi.ErrorStatus
import com.codingblocks.onlineapi.models.Doubts
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.admin_overview_fragment.*
import kotlinx.android.synthetic.main.doubts_fragment.*
import kotlinx.android.synthetic.main.doubts_fragment.nextBtn
import kotlinx.android.synthetic.main.doubts_fragment.prevBtn
import kotlinx.coroutines.Job
import org.jetbrains.anko.AnkoLogger
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit


class DoubtsFragment : Fragment(), AnkoLogger, TabLayout.OnTabSelectedListener {

    companion object {
        fun newInstance() = DoubtsFragment()
    }

    private val doubtsAdapter = DoubtsAdapter()
    private val viewModel by viewModel<DoubtsViewModel>()
    private var job = Job()


    private val ackClickListener: AckClickListener by lazy {
        object : AckClickListener {
            override fun onClick(doubtId: String, doubt: Doubts) {
                viewModel.acknowledgeDoubt(doubtId, doubt)
            }
        }
    }

    private val resolveClickListener: ResolveClickListener by lazy {
        object : ResolveClickListener {
            override fun onClick(doubtId: String, doubt: Doubts) {
                viewModel.acknowledgeDoubt(doubtId, doubt, "238594")
            }
        }
    }

    private val discussClickListener: DiscussClickListener by lazy {
        object : DiscussClickListener {
            override fun onClick(discordId: String) {
                Components.openChrome(requireContext(), "https://discuss.codingblocks.com/t/$discordId")
            }
        }
    }

    private val chatClickListener: ChatClickListener by lazy {
        object : ChatClickListener {
            override fun onClick(convId: String) {
            }
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.doubts_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.fetchLiveDoubts()
        setupWorker()
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adminTabLayout.addOnTabSelectedListener(this)

        doubtRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = doubtsAdapter
        }

        viewModel.listDoubtsResponse.observer(viewLifecycleOwner) {
            doubtsAdapter.submitList(it)
        }

        viewModel.errorLiveData.observer(viewLifecycleOwner)
        {
            when (it) {
                ErrorStatus.EMPTY_RESPONSE -> {

                }
                ErrorStatus.NO_CONNECTION -> {

                }
                ErrorStatus.UNAUTHORIZED -> {
                    Components.showConfirmation(requireContext(), UNAUTHORIZED) {
                        requireActivity().finish()
                    }
                }
                ErrorStatus.TIMEOUT -> {

                }
            }
        }

        doubtsAdapter.apply {
            onAckClick = ackClickListener
            onChatClick = chatClickListener
            onDiscussClick = discussClickListener
            onResolveClick = resolveClickListener
        }
        viewModel.nextOffSet.observer(viewLifecycleOwner) { offSet ->
            nextBtn.isEnabled = offSet != 0
            nextBtn.setOnClickListener {
                viewModel.fetchLiveDoubts(offSet)
            }
        }

        viewModel.prevOffSet.observer(viewLifecycleOwner) { offSet ->
            prevBtn.isEnabled = offSet != 0
            prevBtn.setOnClickListener {
                viewModel.fetchLiveDoubts(offSet)
            }
        }

    }

    override fun onTabReselected(tab: TabLayout.Tab) {
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        //Clear Doubts While Changing the tab
        doubtsAdapter.clear()

        when (tab.position) {
            0 -> {
                viewModel.fetchLiveDoubts()
//                CoroutineScope(Dispatchers.Default + job).launch {
//                    repeat(12) {
//                        delay(10000)
//                        viewModel.fetchLiveDoubts()
//                    }
//                }
            }
            1 -> {
                viewModel.fetchMyDoubts("238594")
            }
        }
    }

    private fun setupWorker() {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<DoubtWorker>(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance()
            .enqueue(request)
    }

    override fun onDestroyView() {
        doubtsAdapter.apply {
            onAckClick = null
            onResolveClick = null
            onChatClick = null
            onDiscussClick = null
        }
        super.onDestroyView()
    }

}
