$(document).ready(function(){
    var map;  
    var ref;
    var token;

    function goToError(){

        setTimeout(function(){
            $('body').addClass('fadeout');
        }, 3000);

        var errorId = document.getElementById("error");
        errorId.style.display = "block";

        var mapId = document.getElementById("map-canvas");
        mapId.style.display = "none"; 
    }
    
    function getToken(urlQS){
        $.ajax({
            url: "http://ridesafe.modev.systems/cgi-bin/makeAuthToken.py",
            type: "post",
            datatype: "text",
            data: {"URL": urlQS},
            success: function(scriptToken){
                token=String(scriptToken);

            }
        })
        
    }
    
    function initialize(){
        // Get a reference to our posts
        var myLatlng = null;
        var prevLatlng = null
        var marker = null;
        var firstMarker = null;
        
        map = new google.maps.Map(document.getElementById('map-canvas'), {center: myLatlng, zoom: 17});

            ref.child(queryString).on("child_added", function(snapshot) {
            var gpsCord = snapshot.val();
            //create Latlng maps var using gpsCord String
            var resStr = gpsCord.split(" ");
            var lat=Number(resStr[0]);
            var lng=Number(resStr[1]);
            
           
            //connect the dots
            prevLatlng = myLatlng;
            myLatlng = new google.maps.LatLng(lat,lng);
            
            //set begining point
            if(prevLatlng==null && myLatlng!=null){
                    firstMarker = new google.maps.Marker({
                    position: myLatlng,
                    title:"Starting Position"
                });
                firstMarker.setMap(map);
            }
                
            // Add to poly line and update/re-center Map
            if(prevLatlng!=null&&myLatlng!=null&&prevLatlng&&prevLatlng!=myLatlng){
                
                var flightPath = new google.maps.Polyline({
                path: [prevLatlng,myLatlng],
                geodesic: true,
                strokeColor: '#D916B0',
                strokeOpacity: 1.0,
                strokeWeight: 10
              });
                //remove old marker and add a new one for the current local
                
                if(marker!=null){
                     marker.setMap(null);
                }
                
                marker = new google.maps.Marker({
                    position: myLatlng,
                    title:"Last Recorded Position"
                });
                marker.setMap(map);
                
               
                
                flightPath.setMap(map);
                map.setCenter(myLatlng);
            }//end null if

            });
        
        }//end init
    //get tokens from makeAuthToken.py
     
    //Create firebase reference, start Auth
    function construct(){
            ref = new Firebase("https://ridesafe.firebaseio.com/");
        
            ref.authWithCustomToken(token, function(error, authData) { 
                 if (error) {
                     goToError();
                  } else {
                    ref = new Firebase("https://ridesafe.firebaseio.com/");
                    var count=0;
                           
                    ref.child(queryString).once('value', function(snapshot) {
                    count++;
                    if (snapshot.val() === null) {
                        goToError();
                    } 
                    else {
                        //if we got a good child, create a map and load it up with points, fadeout of loading screen
                           google.maps.event.addDomListener(window, 'load', initialize());

                           google.maps.event.addListenerOnce(map, 'tilesloaded', function(){$('body').addClass('fadeout'); });
                    }//end else

                    });//end firebae ref
                      
                      //if(count==0){ //meaning it never found a child and thus never transitioned to a map
                    //     goToError();   
                    //    }

                  }//end auth else
            }, {
              remember: "sessionOnly"//delete user at browser close
            });
        
    }
    
        //queryStrig extraction and firebase child check
        var queryString = window.location.search;
        queryString = queryString.substring(1);
        if(queryString.length == 0){ 
            goToError();
        }
        else{
            //get tokens from makeAuthToken.py, then go to constructor 
            getToken(queryString);
            setTimeout(function(){
                construct();
                
            }, 3000);
        }
    
});//end doc ready