/*
 * AbstractEmptyContentAction.java
 *
 * Copyright (c) 2014, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.audiokit.core.service.providers;

public interface AbstractEmptyContentAction {

    public String getDescription();

    public String getActionDescription();

    public AbstractProviderAction getAction();
}
