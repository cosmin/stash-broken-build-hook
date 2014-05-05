# Broken Build Hook for Stash

The Broken Build Hook for Stash is a hook that will prevent people
from pushing commits to the default branch (usually master) if the
build is broken. Build information is retrieved from the Stash Build
Integration API.

In order to allow commits that claim to fix the broken build, the hook
will allow users to override it by adding a special message to their
commit of the form: 'fixes a1b2c3'.

## Installation

This add-on is available for free on the [Atlassian Marketplace]("https://marketplace.atlassian.com/plugins/com.risingoak.stash.plugins.stash-broken-build-hook").

## License

Copyright 2013, Rising Oak LLC.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
    
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Support

The free version of this add-on is not supported. Commercial support
is available. Contact sales@risingoak.com for details.

## Known issues
Cannot be compiled cause of bug in maven-amps-plugin-5.0-m1.jar in central repo.
Please replace downloaded one with corrected from amps-jar folder.