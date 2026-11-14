package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.minecraft.model.ItemCategory
import com.example.minecraft.model.ItemType
import com.example.minecraft.ui.CategoryTabIcon
import com.example.minecraft.ui.ItemIcon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nick Craft", appName)
  }

  @Test
  fun `all 31 items and category tabs render custom pixel-art icons`() {
    composeRule.setContent {
      ItemType.entries.forEach { item ->
        ItemIcon(item = item)
      }
      ItemCategory.entries.forEach { cat ->
        CategoryTabIcon(category = cat)
      }
    }
    composeRule.waitForIdle()
  }
}
