These are example responses from various OTP version.

Response from OTP 0.11: otp-plan-pretty.xml
<response>
...
        <itineraries>
                <itinerary> ... </itineraries>
                <itinerary> ... </itineraries>
                ...
        </itineraries>

Response from OTP 0.18: otp-0.18-plan-pretty.xml
Note nested itineraries tags, each inner itineraries tag should instead be labeled "itinerary"
Note also 'response' tag now has a capital R: 'Response'.
<Response>
... 
        <itineraries>
                <itineraries> ... </itineraries>
                <itineraries> ... </itineraries>
                ...
        </itineraries>
