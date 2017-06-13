/*
 * Copyright (C) 2017-2017 DataStax Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.internal.core.config.typesafe;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.config.DriverConfigProfile;
import com.datastax.oss.driver.api.core.config.DriverOption;
import com.google.common.collect.MapMaker;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class TypesafeDriverConfigProfile implements DriverConfigProfile {

  /** The original profile in the driver's configuration that this profile was derived from. */
  protected abstract Base getBaseProfile();

  /** The extra options that were added with {@code withXxx} methods. */
  protected abstract Config getAddedOptions();

  /** The actual options that will be used to answer {@code getXxx} calls. */
  protected abstract Config getEffectiveOptions();

  @Override
  public boolean isDefined(DriverOption option) {
    return getEffectiveOptions().hasPath(option.getPath());
  }

  @Override
  public boolean getBoolean(DriverOption option) {
    return getEffectiveOptions().getBoolean(option.getPath());
  }

  @Override
  public DriverConfigProfile withBoolean(DriverOption option, boolean value) {
    return with(option, value);
  }

  @Override
  public int getInt(DriverOption option) {
    return getEffectiveOptions().getInt(option.getPath());
  }

  @Override
  public DriverConfigProfile withInt(DriverOption option, int value) {
    return with(option, value);
  }

  @Override
  public Duration getDuration(DriverOption option) {
    return getEffectiveOptions().getDuration(option.getPath());
  }

  @Override
  public DriverConfigProfile withDuration(DriverOption option, Duration value) {
    return with(option, value);
  }

  @Override
  public String getString(DriverOption option) {
    return getEffectiveOptions().getString(option.getPath());
  }

  @Override
  public DriverConfigProfile withString(DriverOption option, String value) {
    return with(option, value);
  }

  @Override
  public List<String> getStringList(DriverOption option) {
    return getEffectiveOptions().getStringList(option.getPath());
  }

  @Override
  public DriverConfigProfile withStringList(DriverOption option, List<String> value) {
    return with(option, value);
  }

  @Override
  public long getBytes(DriverOption option) {
    return getEffectiveOptions().getBytes(option.getPath());
  }

  @Override
  public DriverConfigProfile withBytes(DriverOption option, long value) {
    return with(option, value);
  }

  @Override
  public ConsistencyLevel getConsistencyLevel(DriverOption option) {
    String name = getString(option);
    return ConsistencyLevel.valueOf(name);
  }

  @Override
  public DriverConfigProfile withConsistencyLevel(DriverOption option, ConsistencyLevel value) {
    return with(option, value.toString());
  }

  private DriverConfigProfile with(DriverOption option, Object value) {
    Base base = getBaseProfile();
    // Add the new option to any already derived options
    Config newAdded =
        getAddedOptions().withValue(option.getPath(), ConfigValueFactory.fromAnyRef(value));
    Derived derived = new Derived(base, newAdded);
    base.register(derived);
    return derived;
  }

  /** A profile that was loaded directly from the driver's configuration. */
  static class Base extends TypesafeDriverConfigProfile {

    private volatile Config options;
    private volatile Set<Derived> derivedProfiles;

    Base(Config options) {
      this.options = options;
    }

    @Override
    protected Base getBaseProfile() {
      return this;
    }

    @Override
    protected Config getAddedOptions() {
      return ConfigFactory.empty();
    }

    @Override
    protected Config getEffectiveOptions() {
      return options;
    }

    void refresh(Config newOptions) {
      this.options = newOptions;
      if (derivedProfiles != null) {
        for (Derived derivedProfile : derivedProfiles) {
          derivedProfile.refresh();
        }
      }
    }

    void register(Derived derivedProfile) {
      getDerivedProfiles().add(derivedProfile);
    }

    // Lazy init
    private Set<Derived> getDerivedProfiles() {
      Set<Derived> result = derivedProfiles;
      if (result == null) {
        synchronized (this) {
          result = derivedProfiles;
          if (result == null) {
            derivedProfiles =
                result = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());
          }
        }
      }
      return result;
    }
  }

  /**
   * A profile that was copied from another profile programatically using {@code withXxx} methods.
   */
  static class Derived extends TypesafeDriverConfigProfile {

    private final Base baseProfile;
    private final Config addedOptions;
    private volatile Config effectiveOptions;

    Derived(Base baseProfile, Config addedOptions) {
      this.baseProfile = baseProfile;
      this.addedOptions = addedOptions;
      refresh();
    }

    void refresh() {
      this.effectiveOptions = addedOptions.withFallback(baseProfile.getEffectiveOptions());
    }

    @Override
    protected Base getBaseProfile() {
      return baseProfile;
    }

    @Override
    protected Config getAddedOptions() {
      return addedOptions;
    }

    @Override
    protected Config getEffectiveOptions() {
      return effectiveOptions;
    }
  }
}
