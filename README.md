An app that that is built to help people with vision impairments navigate and find objects within unknown indoor environments. For this it uses a POMDP-based policy to generate a location for the user to point the phone to where the desired object might be located and is based on what the camera is currently seeing to get a better understanding of the current environment. For this, it uses a Tensorflow-based object classifier to feed the current observation into the POMDP. The phone provides audio navigation cues for the user. 