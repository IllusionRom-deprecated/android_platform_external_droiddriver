/*
 * Copyright (C) 2013 UiDriver committers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.uidriver.uiautomation;

import com.google.android.uidriver.InputInjector;
import com.google.android.uidriver.Matcher;
import com.google.android.uidriver.ScrollDirection;
import com.google.android.uidriver.UiElement;
import com.google.android.uidriver.actions.Action;
import com.google.android.uidriver.actions.ClickAction;
import com.google.android.uidriver.actions.SwipeAction;
import com.google.android.uidriver.actions.TypeAction;
import com.google.android.uidriver.exceptions.ElementNotFoundException;
import com.google.android.uidriver.exceptions.ElementNotVisibleException;
import com.google.android.uidriver.exceptions.TimeoutException;
import com.google.common.base.Preconditions;

import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A UiElement that is backed by the UiAutomation object.
 */
public class UiAutomationElement implements UiElement {

  private static final String TAG = UiAutomationDriver.class.getSimpleName();

  private final UiAutomation uiAutomation;
  private final AccessibilityNodeInfo node;
  private final InputInjector injector;

  public UiAutomationElement(UiAutomation uiAutomation, AccessibilityNodeInfo node) {
    this.uiAutomation = Preconditions.checkNotNull(uiAutomation);
    this.node = Preconditions.checkNotNull(node);
    this.injector = new UiAutomationInputInjector(uiAutomation);
  }

  @Override
  public String getText() {
    return charSequenceToString(node.getText());
  }

  @Override
  public void setText(String text) {
    checkVisible();
    perform(new TypeAction(text));
  }

  @Override
  public String getContentDescription() {
    return charSequenceToString(node.getContentDescription());
  }

  @Override
  public String getClassName() {
    return charSequenceToString(node.getClassName());
  }

  @Override
  public UiElement findElement(Matcher matcher) {
    int childCount = node.getChildCount();
    Log.d(TAG, "Looping through number of childs " + childCount);
    for (int i = 0; i < childCount; i++) {
      AccessibilityNodeInfo childNode = node.getChild(i);
      if (childNode == null) {
        Log.w(TAG, "Found null child node for node: " + node);
        continue;
      }
      UiElement element = UiAutomationDrivers.newUiAutomationElement(uiAutomation, childNode);
      if (matcher.matches(element)) {
        Log.d(TAG, "Found match: " + node.getChild(i));
        return element;
      } else {
        //TODO: Remove since this is spammy, or put behind some debug if block.
        Log.d(TAG, "Not found match: " + node.getChild(i));
        try {
          UiElement foundElement = element.findElement(matcher);
          return foundElement;
        } catch (ElementNotFoundException enfe) {
          // Do nothing.  Continue searching.
        }
      }
    }
    throw new ElementNotFoundException(
        "Could not find any matching element for selector: " + matcher);
  }

  @Override
  public UiElement waitForElement(Matcher matcher) {
    //TODO: Make this configurable
    final int timeoutMillis = 10000;
    final int intervalMillis = 500;
    long end = SystemClock.uptimeMillis() + timeoutMillis;
    while (true) {
      try {
        return findElement(matcher);
      } catch (ElementNotFoundException e) {
        // Do nothing.
      }

      if (SystemClock.uptimeMillis() > end) {
        throw new TimeoutException(
            String.format("Timed out after %d milliseconds waiting for element %s",
                timeoutMillis, matcher));
      }
      SystemClock.sleep(intervalMillis);
    }
  }

  @Override
  public boolean perform(Action action) {
    return action.perform(injector, this);
  }

  @Override
  public void click() {
    checkVisible();
    perform(new ClickAction());
  }

  @Override
  public boolean isVisible() {
    return node.isVisibleToUser();
  }

  @Override
  public void scroll(ScrollDirection direction) {
    checkVisible();
    perform(new SwipeAction(direction, false));
  }

  private void checkVisible() {
    if (!isVisible()) {
      throw new ElementNotVisibleException("Element is not visible on screen");
    }
  }

  private String charSequenceToString(CharSequence input) {
    return input == null ? null : input.toString();
  }

  @Override
  public Rect getRect() {
    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    return rect;
  }
}