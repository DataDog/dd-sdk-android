#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

API_SURFACE_PATH = "dd-sdk-android/apiSurface"
NIGHTLY_TESTS_DIRECTORY_PATH = "instrumented/nightly-tests/src/androidTest/kotlin"
NIGHTLY_TESTS_PACKAGE = "com/datadog/android/nightly"
IGNORED_ENTITIES = ["errorevent",
                    "viewevent",
                    "resourceevent",
                    "actionevent",
                    "longtaskevent",
                    "logevent",
                    "spanevent",
                    "rumresourcekind",
                    "userinfo",
                    "networkinfo",
                    "eventmapper",
                    "spaneventmapper",
                    "vieweventmapper",
                    "datadogcontext",
                    "datadogrumcontext",
                    "rumresourceinputstream",
                    "acceptallactivities",
                    "acceptalldefaultfragment",
                    "acceptallnavdestinations",
                    "acceptallsupportfragments",
                    "activitylifecycletrackingstrategy"
                    "activityviewtrackingstrategy",
                    "fragmentviewtrackingstrategy",
                    "componentpredicate",
                    "mixedviewtrackingstrategy",
                    "navigationviewtrackingstrategy",
                    "trackingstrategy",
                    "rumwebviewclient",
                    "datadogdatabaseerrorhandler",
                    "tracedrequestlistener",
                    "tracinginterceptor",
                    "datadogconfig",
                    "datadogeventlistener",
                    "eventlistener",
                    "inputstream",
                    "viewtrackingstrategy",
                    "ondestinationchangedlistener",
                    "databaseerrorhandler"]
