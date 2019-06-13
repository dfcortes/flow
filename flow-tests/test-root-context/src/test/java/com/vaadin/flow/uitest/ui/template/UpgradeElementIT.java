/*
 * Copyright 2000-2018 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.uitest.ui.template;

import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import com.vaadin.flow.testutil.ChromeBrowserTest;
import com.vaadin.testbench.TestBenchElement;

public class UpgradeElementIT extends ChromeBrowserTest {

    @Test
    public void twoWayDatabindingForUpgradedElement() {
        open();

        findElement(By.id("upgrade")).click();

        TestBenchElement template = $(TestBenchElement.class).id("template");
        WebElement input = template.$(TestBenchElement.class).id("input");
        input.sendKeys("foo");
        input.sendKeys(Keys.ENTER);
        WebElement result = findElement(By.id("text-update"));
        Assert.assertEquals("foo", result.getText());
    }
}
