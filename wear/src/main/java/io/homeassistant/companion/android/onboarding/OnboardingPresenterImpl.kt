package io.homeassistant.companion.android.onboarding

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

class OnboardingPresenterImpl @Inject constructor(
    private val view: OnboardingView,
    private val authenticationUseCase: AuthenticationRepository,
    private val urlUseCase: UrlRepository
) : OnboardingPresenter {
    companion object {
        private const val TAG = "OnboardingPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: [${dataEvents.count}]")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        Log.d(TAG, "onDataChanged: found home_assistant_instance")
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        view.onInstanceFound(instance)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        view.onInstanceLost(instance)
                    }
                }
            }
        }
    }

    override fun getInstance(map: DataMap): HomeAssistantInstance {
        map.apply {
            return HomeAssistantInstance(
                getString("name", ""),
                URL(getString("url", "")),
                getString("version", "")
            )
        }
    }

    override fun onAdapterItemClick(instance: HomeAssistantInstance) {
        Log.d(TAG, "onAdapterItemClick: ${instance.name}")
        view.showLoading()
        mainScope.launch {
            // Set current url
            urlUseCase.saveUrl(instance.url.toString())

            // Initiate login flow
            try {
                val flowInit: LoginFlowInit = authenticationUseCase.initiateLoginFlow()
                Log.d(TAG, "Created login flow step ${flowInit.stepId}: ${flowInit.flowId}")

                view.startAuthentication(flowInit.flowId)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to initiate login flow", e)
                view.showError()
            }
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
