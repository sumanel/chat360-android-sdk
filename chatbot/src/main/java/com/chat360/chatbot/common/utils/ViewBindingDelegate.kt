package com.chat360.chatbot.common.utils

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.viewbinding.ViewBinding
import javax.inject.Provider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun <T : ViewBinding> Fragment.viewBinding(viewBindingFactory: (LayoutInflater) -> T) =
    FragmentScopedInstanceDelegate(this, Provider { viewBindingFactory(layoutInflater) })

fun <T> Fragment.scopeInstanceToFragmentView(instanceCreator: () -> T) =
    FragmentScopedInstanceDelegate(this, Provider { instanceCreator() })

inline fun <T : ViewBinding> AppCompatActivity.viewBinding(
    crossinline bindingInflater: (LayoutInflater) -> T
) = lazy(LazyThreadSafetyMode.NONE) { bindingInflater.invoke(layoutInflater) }

class FragmentScopedInstanceDelegate<T>(
    val fragment: Fragment, private val instanceProvider: Provider<T>
) : ReadOnlyProperty<Fragment, T> {

    private var instance: T? = null

    init {
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                fragment.viewLifecycleOwnerLiveData.observe(fragment,
                    Observer<LifecycleOwner> { viewLifecycleOwner ->
                        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                            override fun onDestroy(owner: LifecycleOwner) {
                                instance = null
                            }
                        })
                    })
            }
        })
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return instance ?: with(fragment) {
            if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                throw IllegalStateException("Should not attempt to get bindings when Fragment views are destroyed.")
            }
            instanceProvider.get().also { this@FragmentScopedInstanceDelegate.instance = it }
        }
    }
}
